package com.pluchon.meng.companion.service;

import com.pluchon.meng.api.companion.event.StageReachedEvent;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.client.UserClient;
import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.controller.CompanionDTO;
import com.pluchon.meng.companion.converter.CompanionConverter;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 完整轮次通过审核后，在同一事务中保存气泡、作品、好感和记忆
@Service
public class ReplyService {
    @Autowired
    private CompanionService companion;
    @Autowired
    private CompanionMapper mapper;
    @Autowired
    private CompanionLimits limits;
    @Autowired
    private CompanionProperties properties;
    @Autowired
    private WorkStorage storage;
    @Autowired
    private AiClient ai;
    @Autowired
    private UserClient users;
    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private ThreadPoolTaskExecutor companionExecutor;
    @Autowired
    private ApplicationEventPublisher events;
    @Autowired
    private Clock clock;
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public DeferredResult<ResponseEntity<?>> send(long user, long sessionId, CompanionDTO.Send body) {
        companion.requireSession(user, sessionId);
        limits.checkCooldown(user);
        String token = limits.lock(sessionId);
        var emitter = new SseEmitter(115_000L);
        var response = new DeferredResult<ResponseEntity<?>>(115_000L);
        AtomicBoolean disconnected = new AtomicBoolean();
        response.onTimeout(() -> {
            disconnected.set(true);
            response.setResult(ResponseEntity.status(503).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.failure(CompanionError.AI_UNAVAILABLE, null)));
        });
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onError(error -> disconnected.set(true));
        try {
            companionExecutor.execute(() -> run(user, sessionId, body, token, emitter, response, disconnected));
        } catch (RuntimeException error) {
            limits.unlock(sessionId, token);
            throw new BusinessException(CompanionError.AI_UNAVAILABLE);
        }
        return response;
    }

    private record Prepared(Rows.Session session, Rows.Bond bond, Rows.Message input, List<String> images) {}

    private Prepared prepare(long user, long sessionId, CompanionDTO.Send body) {
        return transactions.execute(status -> {
            var found = companion.requireSession(user, sessionId);
            var bond = mapper.lockBond(user, found.getCharacterCode());
            var session = mapper.lockSession(sessionId, user);
            if (session == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            var images = new ArrayList<String>();
            for (Long id : body.imageIds()) {
                String path = mapper.image(id, user);
                if (path == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                images.add(storage.thumbnail(path));
            }
            var input = new Rows.Message();
            input.setSessionId(sessionId);
            input.setRole("USER");
            input.setContent(body.content().strip());
            input.setImageIds(json.writeValueAsString(body.imageIds()));
            mapper.insertMessage(input);
            return new Prepared(session, bond, input, List.copyOf(images));
        });
    }

    private void run(long user, long sessionId, CompanionDTO.Send body, String token, SseEmitter emitter,
                     DeferredResult<ResponseEntity<?>> response, AtomicBoolean disconnected) {
        CompanionLimits.Reservation reservation = null;
        List<String> savedFiles = new ArrayList<>();
        boolean committed = false;
        try {
            var prepared = prepare(user, sessionId, body);
            reservation = limits.reserve(user);
            var request = context(user, prepared, reservation);
            if (disconnected.get()) return;
            JsonNode result = ai.reply(request, (type, data) -> {
                if (disconnected.get()) throw new IllegalStateException("Companion stream interrupted");
                open(response, emitter);
                emit(emitter, type, data);
            });
            if (disconnected.get() || !limits.locked(sessionId, token)) return;
            List<Rows.Work> works = decodeWorks(user, prepared, result, reservation, savedFiles);
            var output = persist(user, prepared, result, works);
            committed = true;
            String label = result.path("label").asString();
            List<String> used = new ArrayList<>();
            if (!"CRISIS".equals(label)) {
                used.add("message");
                works.forEach(w -> used.add("DOCUMENT".equals(w.getKind()) ? "document" : "image"));
            }
            limits.finish(reservation, used);
            reservation = null;
            if (!"CRISIS".equals(label)) limits.play(user);
            open(response, emitter);
            for (Map<String, Object> event : output) emit(emitter, event.get("event").toString(), event.get("data"));
            if ("CRISIS".equals(label)) emit(emitter, "status", Map.of("kind", "CRISIS_HELP", "hotlines", properties.getHotlines()));
            if ("SEXUAL".equals(label) || "JAILBREAK".equals(label)) {
                LocalDate date = today();
                LocalDateTime start = date.atStartOfDay(ZoneId.of("Asia/Taipei")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                if (mapper.blocks(user, start, start.plusDays(1)) >= 3) {
                    limits.cooldown(user);
                    mapper.safety(user, prepared.session().getCharacterCode(), prepared.input().getId(), "COOLDOWN");
                }
            }
            emit(emitter, "done", Map.of("usage", limits.usage(user)));
            emitter.complete();
        } catch (Exception error) {
            if (!disconnected.get()) {
                var code = error instanceof BusinessException business ? business.errorCode() : CompanionError.AI_UNAVAILABLE;
                try {
                    if (!response.hasResult()) response.setResult(ResponseEntity.status(code.httpStatus()).contentType(MediaType.APPLICATION_JSON).body(ApiResponse.failure(code, null)));
                    else emit(emitter, "error", Map.of("code", code.code()));
                    emitter.complete();
                }
                catch (Exception ignored) { emitter.completeWithError(new IllegalStateException("Companion stream interrupted")); }
            }
        } finally {
            try {
                if (reservation != null) limits.finish(reservation, List.of());
            } finally {
                if (!committed) savedFiles.forEach(storage::delete);
                limits.unlock(sessionId, token);
            }
        }
    }

    private void open(DeferredResult<ResponseEntity<?>> response, SseEmitter emitter) {
        if (!response.hasResult()) response.setResult(ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
            .header("Cache-Control", "no-store").header("X-Accel-Buffering", "no").body(emitter));
    }

    private Map<String, Object> context(long user, Prepared prepared, CompanionLimits.Reservation reservation) {
        var session = prepared.session();
        Long boundary = mapper.recentBoundary(session.getId(), session.getSummaryUntilMessageId());
        long after = boundary == null ? session.getSummaryUntilMessageId() : boundary - 1;
        var history = new ArrayList<>(mapper.history(session.getId(), after));
        Collections.reverse(history);
        var old = boundary == null ? List.<Rows.Message>of() : mapper.summaryBatch(session.getId(), session.getSummaryUntilMessageId(), boundary);
        if (!old.isEmpty()) {
            String summary = ai.summarize(session.getSummary(), turns(old));
            if (summary != null) transactions.executeWithoutResult(status -> {
                var bond = mapper.lockBond(user, session.getCharacterCode());
                if (bond.getRevision() == prepared.bond().getRevision() && mapper.lockSession(session.getId(), user) != null) {
                    mapper.summary(session.getId(), summary, old.get(old.size() - 1).getId(), session.getSummaryUntilMessageId());
                    session.setSummary(summary);
                }
            });
        }
        String nickname = "你";
        try {
            var response = users.users(List.of(user));
            if (response != null && "OK".equals(response.code()) && response.data() != null && !response.data().isEmpty()) nickname = response.data().get(0).nickname();
        } catch (Exception ignored) { }
        var local = clock.instant().atZone(ZoneId.of("Asia/Taipei"));
        String mood = "当前时间：" + local.toLocalDateTime() + "；上次聊天：" + prepared.bond().getLastChatTime() + "（UTC）";
        if (limits.restReminder(user)) mood += "；已经连续聊天约一小时，请自然提出休息";
        var request = new LinkedHashMap<String, Object>();
        request.put("userId", user);
        request.put("character", session.getCharacterCode());
        request.put("stage", prepared.bond().getStage());
        request.put("nickname", nickname == null ? "你" : nickname);
        request.put("mood", mood);
        request.put("memories", mapper.memories(user, session.getCharacterCode()).stream().map(Rows.Memory::getContent).toList());
        request.put("summary", session.getSummary());
        request.put("history", turns(history));
        request.put("content", prepared.input().getContent());
        request.put("images", prepared.images());
        request.put("tools", limits.tools(reservation));
        request.put("messageAllowed", reservation.messageAllowed());
        return request;
    }

    private List<Map<String, String>> turns(List<Rows.Message> history) {
        // 连发气泡合并为一条 assistant 消息，避免把气泡条数当作轮次。
        var turns = new ArrayList<Map<String, String>>();
        for (var message : history) {
            if (!List.of("USER", "CHARACTER").contains(message.getRole())) continue;
            if (!turns.isEmpty() && turns.get(turns.size() - 1).get("role").equals(message.getRole())) {
                var previous = turns.remove(turns.size() - 1);
                turns.add(Map.of("role", message.getRole(), "content", previous.get("content") + "\n" + message.getContent()));
            } else turns.add(Map.of("role", message.getRole(), "content", message.getContent()));
        }
        return turns;
    }

    private List<Rows.Work> decodeWorks(long user, Prepared prepared, JsonNode result, CompanionLimits.Reservation reservation, List<String> savedFiles) {
        var works = new ArrayList<Rows.Work>();
        var used = new java.util.HashSet<String>();
        for (var node : result.path("works")) {
            String kind = node.path("kind").asString();
            if (!List.of("DOCUMENT", "IMAGE").contains(kind) || !used.add(kind)) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            if (!reservation.kinds().contains("DOCUMENT".equals(kind) ? "document" : "image")) throw new BusinessException(CompanionError.COMPANION_QUOTA_EXCEEDED);
            var work = new Rows.Work();
            work.setUserId(user);
            work.setCharacterCode(prepared.session().getCharacterCode());
            work.setSessionId(prepared.session().getId());
            work.setKind(kind);
            work.setTitle(node.path("title").asString());
            if (work.getTitle().isBlank() || work.getTitle().length() > 80) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            if ("DOCUMENT".equals(kind)) {
                String body = node.path("body").asString();
                if (body.isBlank() || body.length() > 20_000) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                work.setBody(body);
            } else {
                byte[] image = Base64.getDecoder().decode(node.path("image").asString());
                var saved = storage.save(image);
                work.setFilePath(saved.name());
                savedFiles.add(saved.name());
            }
            works.add(work);
        }
        return works;
    }

    private List<Map<String, Object>> persist(long user, Prepared prepared, JsonNode result, List<Rows.Work> works) {
        return transactions.execute(status -> {
            var session = prepared.session();
            var bond = mapper.lockBond(user, session.getCharacterCode());
            if (bond.getRevision() != prepared.bond().getRevision() || mapper.lockSession(session.getId(), user) == null) throw new BusinessException(CommonErrorCode.CONFLICT);
            String label = result.path("label").asString();
            if (!List.of("PASS", "ROMANCE_PUSH", "CRISIS", "SEXUAL", "JAILBREAK").contains(label)) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            boolean crisis = "CRISIS".equals(label);
            boolean blocked = "SEXUAL".equals(label) || "JAILBREAK".equals(label);
            if ((crisis || blocked) && !works.isEmpty()) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            var bubbles = result.path("bubbles");
            if (!bubbles.isArray() || bubbles.isEmpty() || bubbles.size() > 3) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            int oldStage = bond.getStage();
            LocalDate date = today();
            boolean firstToday = !date.equals(bond.getTodayDate());
            int delta = CompanionRules.apply(bond, result.path("affection").asInt(), label, date, properties);
            if (!crisis && !blocked) {
                if (bond.getFirstChatTime() == null) bond.setFirstChatTime(now());
                bond.setLastChatTime(now());
                if (firstToday) bond.setChatDays(bond.getChatDays() + 1);
                mapper.updateBond(bond);
                mapper.touchMemories(user, session.getCharacterCode());
                for (var node : result.path("memories")) {
                    String text = node.asString().strip().replaceAll("\\s+", " ");
                    if (CompanionRules.safeMemory(text)) {
                        var memory = new Rows.Memory();
                        memory.setUserId(user);
                        memory.setCharacterCode(session.getCharacterCode());
                        memory.setContent(text);
                        memory.setContentHash(hash(text));
                        memory.setSourceMessageId(prepared.input().getId());
                        mapper.insertMemory(memory);
                    }
                }
                var memories = mapper.memories(user, session.getCharacterCode());
                for (int index = 50; index < memories.size(); index++) mapper.deleteMemory(memories.get(index).getId(), user);
            }
            if (!"PASS".equals(label) && !"ROMANCE_PUSH".equals(label)) mapper.safety(user, session.getCharacterCode(), prepared.input().getId(), label);
            mapper.completeMessage(prepared.input().getId(), label);
            var output = new ArrayList<Map<String, Object>>();
            int count = 1;
            for (var bubble : bubbles) {
                String content = bubble.asString();
                if (content.isBlank() || content.length() > 500) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                var message = characterMessage(session.getId(), content, label, count == 1 ? delta : 0);
                output.add(Map.of("event", "bubble", "data", CompanionConverter.message(message)));
                count++;
            }
            for (var work : works) {
                mapper.insertWork(work);
                var message = characterMessage(session.getId(), work.getTitle(), label, 0);
                mapper.attachWork(message.getId(), work.getId());
                count++;
                output.add(Map.of("event", "work", "data", CompanionConverter.work(work, true)));
            }
            if (bond.getStage() > oldStage) {
                var c = companion.character(session.getCharacterCode());
                String stageName = json.readTree(c.getStageNames()).get(bond.getStage() - 1).asString();
                String line = CompanionRules.stageLine(c.getCode(), bond.getStage());
                var message = characterMessage(session.getId(), line, "PASS", 0);
                count++;
                events.publishEvent(new StageReachedEvent(user, c.getCode(), c.getName(), bond.getStage(), stageName));
                output.add(Map.of("event", "stage", "data", Map.of("stage", bond.getStage(), "stageName", stageName, "message", CompanionConverter.message(message))));
            }
            mapper.advanceSession(session.getId(), prepared.input().getContent().substring(0, Math.min(20, prepared.input().getContent().length())), count, now());
            return output;
        });
    }

    private Rows.Message characterMessage(long sessionId, String content, String label, int delta) {
        var message = new Rows.Message();
        message.setSessionId(sessionId);
        message.setRole("CHARACTER");
        message.setContent(content);
        message.setBubbles(json.writeValueAsString(List.of(content)));
        message.setSafetyLabel(label);
        message.setAffectionDelta(delta);
        message.setCompleted(true);
        message.setCreateTime(now());
        mapper.insertMessage(message);
        return message;
    }

    private void emit(SseEmitter emitter, String type, Object data) {
        try { emitter.send(SseEmitter.event().name(type).data(data)); }
        catch (Exception exception) { throw new IllegalStateException("Companion stream interrupted"); }
    }

    private String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private LocalDate today() { return clock.instant().atZone(ZoneId.of("Asia/Taipei")).toLocalDate(); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
}

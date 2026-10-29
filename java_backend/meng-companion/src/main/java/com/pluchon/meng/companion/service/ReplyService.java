package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.companion.client.*;
import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.controller.*;
import com.pluchon.meng.companion.converter.CompanionConverter;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 请求连接只订阅结果；轮次由数据库和带租约的会话锁管理，断开连接不取消生成。
@Service
public class ReplyService {

    @Autowired
    private CompanionService companion;

    @Autowired
    private CompanionMapper mapper;

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private CompanionLimits limits;

    @Autowired
    private CompanionProperties properties;

    @Autowired
    private CompanionContext contexts;

    @Autowired
    private CompanionOutbox outbox;

    @Autowired
    private AiClient ai;

    @Autowired
    private UserClient users;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ThreadPoolTaskExecutor companionExecutor;

    @Autowired
    private Clock clock;

    @Autowired
    private CompanionTelemetry telemetry;

    @Autowired
    private CompanionRuntimeV2 v2;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();
    private final ConcurrentMap<
        Long,
        CopyOnWriteArrayList<Channel>
    > subscribers = new ConcurrentHashMap<>();

    private static final class Channel {

        final SseEmitter emitter = new SseEmitter(115_000L);
        final DeferredResult<ResponseEntity<?>> response = new DeferredResult<>(
            115_000L
        );
        volatile boolean connected = true;

        Channel() {
            emitter.onCompletion(() -> connected = false);
            emitter.onError(error -> connected = false);
            emitter.onTimeout(() -> {
                connected = false;
                emitter.complete();
            });
            response.onTimeout(() -> {
                connected = false;
                response.setResult(
                    ResponseEntity.status(503)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(
                            ApiResponse.failure(
                                CompanionError.AI_UNAVAILABLE,
                                null
                            )
                        )
                );
            });
        }

        void event(String type, Object data) {
            if (!connected) return;
            try {
                if (!response.hasResult()) response.setResult(
                    ResponseEntity.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .header("Cache-Control", "no-store")
                        .header("X-Accel-Buffering", "no")
                        .body(emitter)
                );
                emitter.send(SseEmitter.event().name(type).data(data));
            } catch (Exception ignored) {
                connected = false;
            }
        }

        void error(ErrorCode code) {
            if (!response.hasResult()) response.setResult(
                ResponseEntity.status(code.httpStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ApiResponse.failure(code, null))
            );
            else event("error", Map.of("code", code.code()));
            emitter.complete();
            connected = false;
        }

        void done(Object usage) {
            event("done", Map.of("usage", usage));
            emitter.complete();
            connected = false;
        }
    }

    public DeferredResult<ResponseEntity<?>> send(
        long user,
        long sessionId,
        CompanionDTO.Send body,
        String zone,
        String ip
    ) {
        String timeZone = CompanionContext.zone(zone);
        var submitted = transactions.execute(status -> {
            var session = companion.requireSession(user, sessionId);
            mapper.ensureBond(user, session.getCharacterCode());
            var bond = mapper.lockBond(user, session.getCharacterCode());
            if (
                mapper.lockSession(sessionId, user) == null
            ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            var previous = runtime.duplicate(sessionId, body.clientMessageId());
            String text = body.content() == null ? "" : body.content().strip();
            if (previous != null) {
                if (
                    !Objects.equals(previous.getContent(), text) ||
                    !json
                        .readTree(previous.getImageIds())
                        .equals(json.valueToTree(body.imageIds()))
                ) throw new BusinessException(CommonErrorCode.CONFLICT);
                if ("FAILED".equals(previous.getTurnState())) {
                    limits.checkCooldown(user);
                    runtime.retry(
                        previous.getId(),
                        bond.getRevision(),
                        timeZone
                    );
                    previous.setTurnState("RECEIVED");
                }
                return previous;
            }
            if (
                text.isBlank() && body.imageIds().isEmpty()
            ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
            limits.checkCooldown(user);
            limits.checkIp(user, ip, text.length());
            for (Long image : body.imageIds())
                if (
                    mapper.image(image, user) == null
                ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
            runtime.zone(user, session.getCharacterCode(), timeZone);
            var message = new Rows.Message();
            message.setSessionId(sessionId);
            message.setRole("USER");
            message.setContent(text);
            message.setImageIds(json.writeValueAsString(body.imageIds()));
            message.setClientMessageId(body.clientMessageId());
            message.setTurnState("RECEIVED");
            message.setKind("TEXT");
            message.setRequestRevision(bond.getRevision());
            message.setTimeZone(timeZone);
            mapper.insertMessage(message);
            return message;
        });
        var channel = new Channel();
        if ("COMMITTED".equals(submitted.getTurnState())) {
            for (var message : runtime.replies(
                sessionId,
                submitted.getTurnId() == null
                    ? submitted.getId()
                    : submitted.getTurnId()
            ))
                replay(channel, user, message);
            channel.done(limits.usage(user, timeZone));
            return channel.response;
        }
        subscribers
            .computeIfAbsent(submitted.getId(), key ->
                new CopyOnWriteArrayList<>()
            )
            .add(channel);
        int started = kick(sessionId);
        if (started == 0) {
            runtime.state(submitted.getId(), submitted.getId(), "FAILED");
            subscribers.remove(submitted.getId());
            channel.error(CompanionError.COMPANION_BUSY);
        } else if (started < 0) {
            // 排队只确认接收，生成结果从历史补齐；相同 clientMessageId 不会新增一条输入。
            channel.event(
                "queued",
                Map.of("clientMessageId", body.clientMessageId())
            );
            channel.done(limits.usage(user, timeZone));
            subscribers.computeIfPresent(submitted.getId(), (key, list) -> {
                list.remove(channel);
                return list.isEmpty() ? null : list;
            });
        }
        return channel.response;
    }

    private void replay(Channel channel, long user, Rows.Message message) {
        channel.event("bubble", CompanionConverter.message(message, true));
    }

    private int kick(long sessionId) {
        String token = limits.tryLock(sessionId);
        if (token == null) return -1;
        var pending = runtime.pending(sessionId);
        if (pending.isEmpty()) {
            limits.unlock(sessionId, token);
            return -1;
        }
        var session = mapper.session(sessionId, owner(sessionId));
        if (session == null) {
            limits.unlock(sessionId, token);
            return -1;
        }
        long user = session.getUserId();
        if (!limits.activate(user, token)) {
            limits.unlock(sessionId, token);
            return 0;
        }
        try {
            companionExecutor.execute(() ->
                run(user, sessionId, token, pending.get(0).getId())
            );
        } catch (RuntimeException error) {
            limits.deactivate(user, token);
            limits.unlock(sessionId, token);
            return 0;
        }
        return 1;
    }

    private long owner(long session) {
        return transactions.execute(status -> {
            var pending = runtime.pending(session);
            if (pending.isEmpty()) return 0L;
            return outbox.sessionOwner(session);
        });
    }

    private record Prepared(
        Rows.Session session,
        Rows.Bond bond,
        List<Rows.Message> inputs,
        String content,
        List<String> images
    ) {}

    private Prepared prepare(long user, long sessionId) {
        return transactions.execute(status -> {
            var found = companion.requireSession(user, sessionId);
            var bond = mapper.lockBond(user, found.getCharacterCode());
            var session = mapper.lockSession(sessionId, user);
            if (session == null) throw new BusinessException(
                CommonErrorCode.NOT_FOUND
            );
            var inputs = new ArrayList<Rows.Message>();
            var images = new ArrayList<String>();
            var text = new StringBuilder();
            for (var input : runtime.pending(sessionId)) {
                var ids = json.readTree(input.getImageIds());
                if (
                    !inputs.isEmpty() &&
                    (text.length() + input.getContent().length() + 1 > 1000 ||
                        images.size() + ids.size() > 3)
                ) break;
                if (input.getRequestRevision() != bond.getRevision()) {
                    runtime.state(input.getId(), input.getId(), "FAILED");
                    continue;
                }
                if (!text.isEmpty()) text.append('\n');
                text.append(input.getContent());
                for (var id : ids) {
                    String file = mapper.image(id.asLong(), user);
                    if (file == null) throw new BusinessException(
                        CommonErrorCode.NOT_FOUND
                    );
                    images.add(outbox.thumbnail(file));
                }
                inputs.add(input);
            }
            if (inputs.isEmpty()) return null;
            long turn = inputs.get(0).getId();
            for (var input : inputs)
                runtime.state(input.getId(), turn, "CONTEXT_READY");
            return new Prepared(
                session,
                bond,
                List.copyOf(inputs),
                text.toString(),
                List.copyOf(images)
            );
        });
    }

    private void run(
        long user,
        long sessionId,
        String token,
        long submittedId
    ) {
        long started = System.nanoTime();
        Prepared prepared = null;
        CompanionLimits.Reservation reservation = null;
        boolean committed = false;
        try {
            prepared = prepare(user, sessionId);
            if (prepared == null) {
                for (var channel : subscribers.getOrDefault(
                    submittedId,
                    new CopyOnWriteArrayList<>()
                ))
                    channel.error(CommonErrorCode.CONFLICT);
                return;
            }
            long turn = prepared.inputs().get(0).getId();
            String zone = prepared.inputs().get(0).getTimeZone();
            reservation = limits.reserve(user, zone);
            var request = context(user, prepared, reservation);
            runtime.groupState(turn, "GENERATING");
            var current = prepared;
            JsonNode result = ai.reply(request, (type, data) ->
                broadcast(current, type, data)
            );
            if (!limits.locked(sessionId, token)) throw new BusinessException(
                CompanionError.AI_UNAVAILABLE
            );
            runtime.groupState(turn, "CHECKED");
            var memoryIds = new ArrayList<Long>();
            for (var id : result
                .path("metrics")
                .path("layers")
                .path("memoryIds"))
                if (
                    ((List<?>) request.get("memoryIds")).contains(id.asLong())
                ) memoryIds.add(id.asLong());
            var output = persist(
                user,
                prepared,
                result,
                reservation,
                memoryIds
            );
            committed = true;
            telemetry.completed(result, System.nanoTime() - started);
            boolean billable =
                result.path("billable").asBoolean(true) &&
                !"CRISIS".equals(result.path("label").asString());
            limits.finish(
                reservation,
                usedKinds(result, reservation, billable)
            );
            reservation = null;
            for (var event : output)
                broadcast(
                    prepared,
                    event.get("event").toString(),
                    event.get("data")
                );
            if ("CRISIS".equals(result.path("label").asString())) broadcast(
                prepared,
                "status",
                Map.of(
                    "kind",
                    "CRISIS_HELP",
                    "hotlines",
                    properties.getHotlines()
                )
            );
            for (var input : prepared.inputs())
                for (var channel : subscribers.getOrDefault(
                    input.getId(),
                    new CopyOnWriteArrayList<>()
                ))
                    channel.done(limits.usage(user, zone));
        } catch (Exception error) {
            ErrorCode code =
                error instanceof BusinessException business
                    ? business.errorCode()
                    : CompanionError.AI_UNAVAILABLE;
            if (prepared != null) {
                if (!committed) runtime.groupState(
                    prepared.inputs().get(0).getId(),
                    "FAILED"
                );
                for (var input : prepared.inputs())
                    for (var channel : subscribers.getOrDefault(
                        input.getId(),
                        new CopyOnWriteArrayList<>()
                    ))
                        channel.error(code);
            } else {
                runtime.state(submittedId, submittedId, "FAILED");
                for (var channel : subscribers.getOrDefault(
                    submittedId,
                    new CopyOnWriteArrayList<>()
                ))
                    channel.error(code);
            }
        } finally {
            try {
                if (reservation != null) limits.finish(reservation, List.of());
            } finally {
                subscribers.remove(submittedId);
                if (prepared != null) for (var input : prepared.inputs())
                    subscribers.remove(input.getId());
                try {
                    limits.deactivate(user, token);
                } finally {
                    limits.unlock(sessionId, token);
                }
                if (!runtime.pending(sessionId).isEmpty()) kick(sessionId);
            }
        }
    }

    private Map<String, Object> context(
        long user,
        Prepared p,
        CompanionLimits.Reservation reservation
    ) {
        var session = p.session();
        String zone = p.inputs().get(0).getTimeZone();
        var memories = contexts.memories(
            user,
            session.getCharacterCode(),
            p.content(),
            zone,
            p.inputs().get(0).getId().toString()
        );
        var rows = new ArrayList<>(
            mapper.history(session.getId(), session.getWindowStartMessageId())
        );
        Collections.reverse(rows);
        String nickname = "你";
        try {
            var response = users.users(List.of(user));
            if (
                response != null &&
                response.data() != null &&
                !response.data().isEmpty()
            ) nickname = response.data().get(0).nickname();
        } catch (RuntimeException ignored) {}
        var request = new LinkedHashMap<String, Object>();
        request.put("userId", user);
        request.put("sessionId", session.getId());
        request.put("character", session.getCharacterCode());
        var card = contexts.card(user, session.getCharacterCode());
        request.put("card", card);
        request.put("nickname", nickname == null ? "你" : nickname);
        var local = clock
            .instant()
            .atZone(ZoneId.of(zone))
            .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        request.put(
            "mood",
            "当地时间：" +
                local.toLocalDateTime() +
                "，星期" +
                "一二三四五六日".charAt(local.getDayOfWeek().getValue() - 1) +
                "；距上一条消息：" +
                (session.getLastMessageTime() == null
                    ? "首次对话"
                    : Duration.between(
                          session.getLastMessageTime(),
                          now()
                      ).toMinutes() + "分钟")
        );
        request.put("timeZone", zone);
        request.put("turnId", p.inputs().get(0).getId().toString());
        var snapshot = v2.snapshot(session, p.bond(), false);
        request.put("contextSnapshot", snapshot);
        var nowMemories = contexts.allMemories(
            user,
            session.getCharacterCode()
        );
        var newMemories = nowMemories
            .stream()
            .filter(
                m ->
                    session.getSnapshotTime() != null &&
                    m.getValidFrom() != null &&
                    m.getValidFrom().isAfter(session.getSnapshotTime())
            )
            .map(m ->
                Map.of(
                    "id",
                    m.getId(),
                    "content",
                    m.getContent(),
                    "kind",
                    m.getKind()
                )
            )
            .toList();
        request.put("newMemories", newMemories);
        request.put("controls", List.of("sticker"));
        request.put("recalled", memories.recalled());
        request.put("priorCalls", memories.calls());
        request.put("examples", contexts.examples(p.bond(), card));
        request.put("recentReplies", runtime.recentReplies(session.getId()));
        request.put("summary", session.getSummary());
        request.put("history", turns(rows));
        request.put(
            "content",
            p.content().isBlank() ? "请看看图片" : p.content()
        );
        request.put("images", p.images());
        request.put("messageAllowed", reservation.messageAllowed());
        request.put("webSearchRemaining", limits.searchRemaining(reservation));
        var allowedMemoryIds = new java.util.HashSet<Long>();
        for (var memory : snapshot.path("memories"))
            if (memory.path("id").isIntegralNumber()) allowedMemoryIds.add(
                memory.path("id").asLong()
            );
        newMemories.forEach(m ->
            allowedMemoryIds.add(((Number) m.get("id")).longValue())
        );
        memories
            .recalled()
            .stream()
            .map(m -> m.get("id"))
            .filter(id -> id instanceof Number)
            .forEach(id -> allowedMemoryIds.add(((Number) id).longValue()));
        request.put("memoryIds", List.copyOf(allowedMemoryIds));
        return request;
    }

    public static List<Map<String, String>> turns(List<Rows.Message> history) {
        var turns = new ArrayList<Map<String, String>>();
        for (var message : history) {
            if (
                !List.of("USER", "CHARACTER").contains(message.getRole()) ||
                "STICKER".equals(message.getKind())
            ) continue;
            if (
                !turns.isEmpty() &&
                turns
                    .get(turns.size() - 1)
                    .get("role")
                    .equals(message.getRole()) &&
                turns
                    .get(turns.size() - 1)
                    .get("content")
                    .length() +
                    1 +
                    message.getContent().length() <=
                    6000
            ) {
                var previous = turns.remove(turns.size() - 1);
                String content =
                    previous.get("content") + "\n" + message.getContent();
                turns.add(
                    Map.of("role", message.getRole(), "content", content)
                );
            } else turns.add(
                Map.of(
                    "role",
                    message.getRole(),
                    "content",
                    message.getContent()
                )
            );
        }
        return turns;
    }

    public static List<String> usedKinds(JsonNode result, CompanionLimits.Reservation reservation,
                                          boolean billable) {
        var used = new ArrayList<String>();
        if (!billable) return List.of();
        used.add("message");
        int searches = 0;
        for (var tool : result.path("toolUsage")) {
            if (!"web_search".equals(tool.path("name").asString())) continue;
            int count = tool.path("searchCalls").asInt(0);
            if (count < 0 || count > 1) throw new IllegalArgumentException("搜索次数不合法");
            if (tool.path("success").asBoolean() && !tool.path("cached").asBoolean()) searches += count;
        }
        if (searches > 1 || (searches > 0 && !reservation.searchAllowed()))
            throw new IllegalArgumentException("搜索超出本轮预留额度");
        if (searches > 0) used.add("search");
        return List.copyOf(used);
    }

    private List<Map<String, Object>> persist(
        long user,
        Prepared p,
        JsonNode result,
        CompanionLimits.Reservation reservation,
        List<Long> memoryIds
    ) {
        return transactions.execute(status -> {
            var session = p.session();
            var bond = mapper.lockBond(user, session.getCharacterCode());
            if (
                bond.getRevision() != p.bond().getRevision() ||
                mapper.lockSession(session.getId(), user) == null
            ) throw new BusinessException(CommonErrorCode.CONFLICT);
            if (
                result.path("metrics") instanceof
                    tools.jackson.databind.node.ObjectNode metrics
            ) metrics.put(
                "windowStartMessageId",
                session.getWindowStartMessageId()
            );
            String label = result.path("label").asString();
            if (
                !Set.of(
                    "PASS",
                    "ROMANCE_PUSH",
                    "CRISIS",
                    "SEXUAL",
                    "JAILBREAK"
                ).contains(label)
            ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            var bubbles = result.path("bubbles");
            if (
                !bubbles.isArray() || bubbles.isEmpty() || bubbles.size() > 3
            ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            boolean billable =
                result.path("billable").asBoolean() && !"CRISIS".equals(label);
            usedKinds(result, reservation, billable);
            if (
                billable && !reservation.messageAllowed()
            ) throw new BusinessException(
                CompanionError.COMPANION_QUOTA_EXCEEDED
            );
            long turn = p.inputs().get(0).getId();
            var output = new ArrayList<Map<String, Object>>();
            int count = p.inputs().size();
            for (var input : p.inputs())
                mapper.completeMessage(input.getId(), label);
            for (var bubble : bubbles) {
                if (
                    !bubble.isString() ||
                    bubble.asString().isBlank() ||
                    bubble.asString().length() > 500
                ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                var message = character(
                    session.getId(),
                    turn,
                    bubble.asString(),
                    label,
                    result
                );
                v2.metadata(message, result);
                output.add(
                    Map.of(
                        "event",
                        "bubble",
                        "data",
                        CompanionConverter.message(message, true)
                    )
                );
                count++;
            }
            String sticker = result.path("sticker").asString("");
            if (!sticker.isBlank()) {
                if (
                    !sticker.matches("[^\\s\\[\\]]{1,12}")
                ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                var message = character(
                    session.getId(),
                    turn,
                    "",
                    label,
                    result
                );
                outbox.sticker(message.getId(), sticker);
                message.setKind("STICKER");
                message.setSticker(sticker);
                output.add(
                    Map.of(
                        "event",
                        "bubble",
                        "data",
                        CompanionConverter.message(message, true)
                    )
                );
                count++;
            }
            mapper.advanceSession(
                session.getId(),
                p.content().substring(0, Math.min(20, p.content().length())),
                count,
                now()
            );
            runtime.unread(session.getId(), user, true);
            if (!"PASS".equals(label)) {
                mapper.safety(user, session.getCharacterCode(), turn, label);
                if (
                    Set.of("SEXUAL", "JAILBREAK").contains(label)
                ) outbox.checkCooldown(
                    user,
                    session.getCharacterCode(),
                    turn,
                    clock
                        .instant()
                        .atZone(ZoneId.of(p.inputs().get(0).getTimeZone()))
                        .toLocalDate()
                );
            }
            if (
                billable && Set.of("PASS", "ROMANCE_PUSH").contains(label)
            ) outbox.enqueue(
                "turn.committed",
                "turn-" + turn,
                Map.of(
                    "userId",
                    user,
                    "characterCode",
                    session.getCharacterCode(),
                    "sessionId",
                    session.getId(),
                    "messageId",
                    turn,
                    "revision",
                    bond.getRevision(),
                    "timeZone",
                    p.inputs().get(0).getTimeZone()
                )
            );
            outbox.messageContext(turn, memoryIds);
            outbox.touch(user, session.getCharacterCode(), memoryIds);
            return output;
        });
    }

    private Rows.Message character(
        long session,
        long turn,
        String content,
        String label,
        JsonNode result
    ) {
        var message = new Rows.Message();
        message.setSessionId(session);
        message.setTurnId(turn);
        message.setRole("CHARACTER");
        message.setContent(content);
        message.setBubbles(json.writeValueAsString(List.of(content)));
        message.setSafetyLabel(label);
        message.setCompleted(true);
        message.setCreateTime(now());
        message.setPromptVersion(result.path("promptVersion").asString());
        message.setModel(result.path("model").asString());
        message.setMetrics(result.path("metrics").toString());
        mapper.insertMessage(message);
        return message;
    }

    private void broadcast(Prepared p, String type, Object data) {
        for (var input : p.inputs())
            for (var channel : subscribers.getOrDefault(
                input.getId(),
                new CopyOnWriteArrayList<>()
            ))
                channel.event(type, data);
    }

    public void recover() {
        runtime.expireTurns();
        for (long session : runtime.waiting()) kick(session);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

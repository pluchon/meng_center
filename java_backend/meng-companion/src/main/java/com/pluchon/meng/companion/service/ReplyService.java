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
        return send(user, sessionId, body, zone, ip, null);
    }

    private DeferredResult<ResponseEntity<?>> send(
        long user,
        long sessionId,
        CompanionDTO.Send body,
        String zone,
        String ip,
        Long regenerates
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
                    !Objects.equals(
                        previous.getRequestWorkId(),
                        body.workId()
                    ) ||
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
            contexts.works(user, session, body.workId());
            runtime.zone(user, session.getCharacterCode(), timeZone);
            var message = new Rows.Message();
            message.setSessionId(sessionId);
            message.setRole("USER");
            message.setContent(text);
            message.setImageIds(json.writeValueAsString(body.imageIds()));
            message.setClientMessageId(body.clientMessageId());
            message.setTurnState("RECEIVED");
            message.setRequestWorkId(body.workId());
            message.setRequestRevision(bond.getRevision());
            message.setTimeZone(timeZone);
            message.setRegeneratesMessageId(regenerates);
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
        if (message.getWorkId() != null) channel.event(
            "work",
            companion.work(user, message.getWorkId())
        );
        else channel.event("bubble", CompanionConverter.message(message));
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
        List<String> images,
        Long work
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
            Long work = null;
            for (var input : runtime.pending(sessionId)) {
                var ids = json.readTree(input.getImageIds());
                if (
                    !inputs.isEmpty() &&
                    (text.length() + input.getContent().length() + 1 > 1000 ||
                        images.size() + ids.size() > 3 ||
                        (work != null &&
                            input.getRequestWorkId() != null &&
                            !work.equals(input.getRequestWorkId())))
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
                if (input.getRequestWorkId() != null) work =
                    input.getRequestWorkId();
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
                List.copyOf(images),
                work
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
            var used = new ArrayList<String>();
            if (billable) {
                used.add("message");
                if (!result.path("works").isEmpty()) used.add("document");
            }
            boolean drawing = !result.path("drawings").isEmpty();
            var finish = new CompanionLimits.Reservation(
                reservation.user(),
                reservation.date(),
                reservation.token(),
                reservation
                    .kinds()
                    .stream()
                    .filter(k -> !drawing || !"image".equals(k))
                    .toList(),
                reservation.messageAllowed()
            );
            limits.finish(finish, used);
            reservation = null;
            if (billable) limits.play(user, zone);
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
        if (p.inputs().get(0).getRegeneratesMessageId() != null) {
            var rejected = runtime.message(
                p.inputs().get(0).getRegeneratesMessageId()
            );
            rows.removeIf(m ->
                Objects.equals(m.getTurnId(), rejected.getTurnId())
            );
        }
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
        request.put("character", session.getCharacterCode());
        request.put("stage", p.bond().getStage());
        request.put("level", p.bond().getLevel());
        request.put("levelChanged", p.bond().isLevelChanged());
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
                "；上次聊天（UTC）：" +
                p.bond().getLastChatTime() +
                (limits.restReminder(user, zone)
                    ? "；已连续聊天约一小时，请自然提出休息"
                    : "")
        );
        request.put("timeZone", zone);
        request.put("turnId", p.inputs().get(0).getId().toString());
        request.put("memories", memories.stable());
        request.put("memoryItems", memories.items());
        request.put("sharedMemory", memories.shared());
        request.put("priorTokens", memories.tokens());
        request.put("profile", p.bond().getProfile());
        request.put("recalled", memories.recalled());
        request.put("due", memories.due());
        request.put("daily", contexts.daily(session.getCharacterCode(), zone));
        request.put("examples", contexts.examples(p.bond(), card));
        request.put(
            "previousFeeling",
            Objects.requireNonNullElse(runtime.feeling(session.getId()), "平静")
        );
        request.put("recentReplies", runtime.recentReplies(session.getId()));
        request.put("works", contexts.works(user, session, p.work()));
        request.put("summary", session.getSummary());
        request.put("history", turns(rows));
        request.put(
            "content",
            p.content().isBlank() ? "请看看图片" : p.content()
        );
        request.put("images", p.images());
        request.put("tools", limits.tools(reservation));
        request.put("messageAllowed", reservation.messageAllowed());
        if (p.inputs().get(0).getRegeneratesMessageId() != null) request.put(
            "tools",
            List.of(
                "search_posts",
                "my_recent_posts",
                "my_growth",
                "recall",
                "read_work"
            )
        );
        request.put("memoryIds", memories.used());
        return request;
    }

    public static List<Map<String, String>> turns(List<Rows.Message> history) {
        var turns = new ArrayList<Map<String, String>>();
        for (var message : history) {
            if (
                !List.of("USER", "CHARACTER").contains(message.getRole()) ||
                message.getWorkId() != null ||
                "STICKER".equals(message.getKind())
            ) continue;
            if (
                !turns.isEmpty() &&
                turns
                    .get(turns.size() - 1)
                    .get("role")
                    .equals(message.getRole())
            ) {
                var previous = turns.remove(turns.size() - 1);
                String content =
                    previous.get("content") + "\n" + message.getContent();
                turns.add(
                    Map.of(
                        "role",
                        message.getRole(),
                        "content",
                        content.substring(0, Math.min(6000, content.length()))
                    )
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
        return turns.size() > 80
            ? turns.subList(turns.size() - 80, turns.size())
            : turns;
    }

    private List<Map<String, Object>> persist(
        long user,
        Prepared p,
        JsonNode result,
        CompanionLimits.Reservation r,
        List<Long> memoryIds
    ) {
        return transactions.execute(status -> {
            var session = p.session();
            var bond = mapper.lockBond(user, session.getCharacterCode());
            if (
                bond.getRevision() != p.bond().getRevision() ||
                mapper.lockSession(session.getId(), user) == null
            ) throw new BusinessException(CommonErrorCode.CONFLICT);
            String label = result.path("label").asString();
            if (
                !List.of(
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
            boolean normal = List.of("PASS", "ROMANCE_PUSH").contains(label);
            boolean billable = result.path("billable").asBoolean(true);
            int oldStage = bond.getStage();
            LocalDate date = clock
                .instant()
                .atZone(ZoneId.of(p.inputs().get(0).getTimeZone()))
                .toLocalDate();
            boolean first = !date.equals(bond.getTodayDate());
            boolean regenerating =
                p.inputs().get(0).getRegeneratesMessageId() != null;
            int delta =
                normal && billable && !regenerating
                    ? CompanionRules.apply(
                          bond,
                          result.path("affection").asInt(),
                          label,
                          date,
                          properties
                      )
                    : 0;
            if (normal && billable) {
                if (bond.getFirstChatTime() == null) bond.setFirstChatTime(
                    now()
                );
                bond.setLastChatTime(now());
                if (first) bond.setChatDays(bond.getChatDays() + 1);
                mapper.updateBond(bond);
            }
            long turn = p.inputs().get(0).getId();
            var output = new ArrayList<Map<String, Object>>();
            int count = p.inputs().size();
            for (var input : p.inputs())
                mapper.completeMessage(input.getId(), label);
            int index = 0;
            for (var bubble : bubbles) {
                String text = bubble.asString();
                if (
                    text.isBlank() || text.length() > 500
                ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                var message = character(
                    session.getId(),
                    turn,
                    text,
                    label,
                    index++ == 0 ? delta : 0,
                    result
                );
                output.add(
                    Map.of(
                        "event",
                        "bubble",
                        "data",
                        CompanionConverter.message(message)
                    )
                );
                count++;
            }
            if (regenerating) outbox.replace(
                user,
                p.inputs().get(0).getRegeneratesMessageId(),
                turn,
                runtime.replies(session.getId(), turn).get(0).getId()
            );
            String sticker = result.path("sticker").asString("");
            if (!sticker.isBlank()) {
                boolean allowed = false;
                for (var id : contexts
                    .card(user, session.getCharacterCode())
                    .path("stickers"))
                    if (sticker.equals(id.asString())) allowed = true;
                if (!allowed) throw new BusinessException(
                    CompanionError.AI_UNAVAILABLE
                );
                var m = character(session.getId(), turn, "", label, 0, result);
                m.setKind("STICKER");
                m.setSticker(sticker);
                outbox.sticker(m.getId(), sticker);
                output.add(
                    Map.of(
                        "event",
                        "bubble",
                        "data",
                        CompanionConverter.message(m)
                    )
                );
                count++;
            }
            if (
                result.path("works").size() > 1 ||
                result.path("drawings").size() > 1
            ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            if (
                !normal &&
                (!result.path("works").isEmpty() ||
                    !result.path("drawings").isEmpty())
            ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
            for (var node : result.path("works")) {
                if (
                    !r.kinds().contains("document") ||
                    !"DOCUMENT".equals(node.path("kind").asString())
                ) throw new BusinessException(
                    CompanionError.COMPANION_QUOTA_EXCEEDED
                );
                var work = new Rows.Work();
                work.setUserId(user);
                work.setCharacterCode(session.getCharacterCode());
                work.setSessionId(session.getId());
                work.setKind("DOCUMENT");
                work.setTitle(node.path("title").asString());
                work.setBody(node.path("body").asString());
                if (
                    work.getTitle().isBlank() ||
                    work.getTitle().length() > 80 ||
                    work.getBody().isBlank() ||
                    work.getBody().length() > 20000
                ) throw new BusinessException(CompanionError.AI_UNAVAILABLE);
                if (
                    !node.path("parentId").isNull() &&
                    node.path("parentId").asLong() > 0
                ) {
                    var parent = companion.requireWork(
                        user,
                        node.path("parentId").asLong()
                    );
                    if (
                        !parent
                            .getCharacterCode()
                            .equals(session.getCharacterCode()) ||
                        !"DOCUMENT".equals(parent.getKind())
                    ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
                    work.setParentId(parent.getId());
                }
                mapper.insertWork(work);
                var m = character(
                    session.getId(),
                    turn,
                    work.getTitle(),
                    label,
                    0,
                    result
                );
                mapper.attachWork(m.getId(), work.getId());
                count++;
                output.add(
                    Map.of(
                        "event",
                        "work",
                        "data",
                        CompanionConverter.work(work, true)
                    )
                );
            }
            for (var drawing : result.path("drawings")) {
                if (!r.kinds().contains("image")) throw new BusinessException(
                    CompanionError.COMPANION_QUOTA_EXCEEDED
                );
                outbox.drawing(
                    user,
                    session.getId(),
                    turn,
                    drawing.path("instruction").asString(),
                    r,
                    bond.getRevision()
                );
                limits.retainImage(r);
            }
            if (bond.getStage() > oldStage) {
                var c = companion.character(session.getCharacterCode());
                String name = json
                    .readTree(c.getStageNames())
                    .get(bond.getStage() - 1)
                    .asString();
                var m = character(
                    session.getId(),
                    turn,
                    contexts
                        .card(user, c.getCode())
                        .path("stageLines")
                        .get(bond.getStage() - 1)
                        .asString(),
                    label,
                    0,
                    result
                );
                count++;
                outbox.enqueue(
                    "companion.stage-reached",
                    "stage-" + turn,
                    Map.of(
                        "userId",
                        user,
                        "characterCode",
                        c.getCode(),
                        "characterName",
                        c.getName(),
                        "stage",
                        bond.getStage(),
                        "stageName",
                        name
                    )
                );
                output.add(
                    Map.of(
                        "event",
                        "stage",
                        "data",
                        Map.of(
                            "stage",
                            bond.getStage(),
                            "stageName",
                            name,
                            "message",
                            CompanionConverter.message(m)
                        )
                    )
                );
            }
            mapper.advanceSession(
                session.getId(),
                p.content().substring(0, Math.min(20, p.content().length())),
                count,
                now()
            );
            runtime.unread(session.getId(), user, true);
            runtime.levelConsumed(
                user,
                session.getCharacterCode(),
                p.bond().getLevel()
            );
            if (!normal) {
                mapper.safety(user, session.getCharacterCode(), turn, label);
                outbox.checkCooldown(
                    user,
                    session.getCharacterCode(),
                    turn,
                    date
                );
            }
            if (normal && billable && !regenerating) outbox.enqueue(
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
            long mentioned = result.path("mentioned_memory_id").asLong(0);
            if (memoryIds.contains(mentioned)) outbox.mentioned(
                user,
                session.getCharacterCode(),
                mentioned
            );
            outbox.touch(user, session.getCharacterCode(), memoryIds);
            return output;
        });
    }

    private Rows.Message character(
        long session,
        long turn,
        String text,
        String label,
        int delta,
        JsonNode result
    ) {
        var message = new Rows.Message();
        message.setSessionId(session);
        message.setTurnId(turn);
        message.setRole("CHARACTER");
        message.setContent(text);
        message.setBubbles(json.writeValueAsString(List.of(text)));
        message.setSafetyLabel(label);
        message.setAffectionDelta(delta);
        message.setCompleted(true);
        message.setCreateTime(now());
        message.setFeeling(result.path("feeling").asString("平静"));
        message.setIntent(result.path("intent").asString("回应"));
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

    public void feedback(long user, long id) {
        var m = runtime.message(id);
        if (
            m == null ||
            !"CHARACTER".equals(m.getRole()) ||
            m.getWorkId() != null
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        companion.requireSession(user, m.getSessionId());
        outbox.feedback(user, id, "OFF_CHARACTER", m.getContent());
    }

    public DeferredResult<ResponseEntity<?>> regenerate(
        long user,
        long id,
        String zone,
        String ip
    ) {
        var m = runtime.message(id);
        if (
            m != null && m.getRegeneratesMessageId() != null
        ) throw new BusinessException(CommonErrorCode.CONFLICT);
        if (
            m == null ||
            !"CHARACTER".equals(m.getRole()) ||
            m.getTurnId() == null ||
            m.getWorkId() != null
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        companion.requireSession(user, m.getSessionId());
        var original = runtime.message(m.getTurnId());
        if (
            outbox.regenerated(user, m.getTurnId()) &&
            runtime.duplicate(m.getSessionId(), "regenerate-" + id) == null
        ) throw new BusinessException(CommonErrorCode.CONFLICT);
        outbox.feedback(user, id, "REGENERATE", m.getContent());
        var images = new ArrayList<Long>();
        for (var image : json.readTree(original.getImageIds()))
            images.add(image.asLong());
        return send(
            user,
            m.getSessionId(),
            new CompanionDTO.Send(
                "regenerate-" + id,
                original.getContent(),
                images,
                null
            ),
            zone,
            ip,
            id
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}

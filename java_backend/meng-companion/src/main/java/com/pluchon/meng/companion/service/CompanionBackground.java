package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 模型计算在事务外进行，提交时重新锁关系并校验 revision；过期结果不能恢复已删除的记忆。
@Service
public class CompanionBackground {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CompanionMapper mapper;

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private AiClient ai;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private CompanionOutbox outbox;

    @Autowired
    private WorkStorage storage;

    @Autowired
    private CompanionLimits limits;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private Clock clock;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public void consolidate(EventEnvelope<JsonNode> event) {
        var body = event.body();
        long user = body.path("userId").asLong(),
            sessionId = body.path("sessionId").asLong();
        String code = body.path("characterCode").asString();
        if (processed(event.eventId())) return;
        var bond = mapper.bond(user, code);
        if (
            bond == null ||
            bond.getRevision() != body.path("revision").asLong() ||
            mapper.session(sessionId, user) == null ||
                !code.equals(mapper.session(sessionId, user).getCharacterCode())
        ) {
            markProcessed(event.eventId());
            return;
        }
        var batch = new ArrayList<
            com.pluchon.meng.common.core.event.EventEnvelope<JsonNode>
        >();
        batch.add(event);
        var position = jdbc.queryForList(
            "SELECT id FROM companion_outbox WHERE event_id=?",
            event.eventId()
        );
        if (!position.isEmpty()) {
            long start = ((Number) position.get(0).get("id")).longValue();
            for (var row : jdbc.queryForList(
                "SELECT event_id,payload FROM companion_outbox WHERE id>? AND id<? AND event_type='turn.committed' ORDER BY id",
                start,
                start + 32
            )) {
                var next = json.readTree(row.get("payload").toString());
                String id = row.get("event_id").toString();
                if (batch.size() >= 5) break;
                if (
                    next.path("userId").asLong() != user ||
                    !code.equals(next.path("characterCode").asString()) ||
                    next.path("revision").asLong() != bond.getRevision() ||
                    processed(id)
                ) continue;
                var nextSession = mapper.session(
                    next.path("sessionId").asLong(),
                    user
                );
                if (
                    nextSession == null ||
                    !code.equals(nextSession.getCharacterCode())
                ) continue;
                batch.add(
                    new EventEnvelope<>(
                        id,
                        "turn.committed",
                        clock.instant(),
                        id,
                        next
                    )
                );
            }
        }
        var history = new ArrayList<Rows.Message>();
        for (var item : batch)
            history.addAll(
                jdbc.query(
                    "SELECT * FROM companion_message WHERE turn_id=? AND session_id=? AND completed=1 AND delete_state=0 AND work_id IS NULL ORDER BY id",
                    new org.springframework.jdbc.core.BeanPropertyRowMapper<>(
                        Rows.Message.class
                    ),
                    item.body().path("messageId").asLong(),
                    item.body().path("sessionId").asLong()
                )
            );
        if (history.isEmpty()) {
            for (var item : batch) markProcessed(item.eventId());
            return;
        }
        var old = new ArrayList<>(mapper.memories(user, code));
        String query = history
            .stream()
            .filter(m -> "USER".equals(m.getRole()))
            .map(Rows.Message::getContent)
            .collect(java.util.stream.Collectors.joining(" "));
        old.sort(
            Comparator.comparingLong((Rows.Memory m) ->
                query
                    .codePoints()
                    .distinct()
                    .filter(c -> m.getContent().indexOf(c) >= 0)
                    .count()
            ).reversed()
        );
        if (old.size() > 20) old.subList(20, old.size()).clear();
        String zone = CompanionContext.zone(body.path("timeZone").asString());
        var result = ai.compute(
            "consolidations",
            Map.of(
                "turns",
                ReplyService.turns(history),
                "memories",
                candidates(old),
                "profile",
                Objects.requireNonNullElse(bond.getProfile(), ""),
                "localDate",
                clock
                    .instant()
                    .atZone(ZoneId.of(zone))
                    .toLocalDate()
                    .toString(),
                "timeZone",
                zone
            )
        );
        validateDecisions(result, old, history);
        var today = clock.instant().atZone(ZoneId.of(zone)).toLocalDate();
        for (var d : result.path("decisions"))
            if (
                !d.path("dueDate").isNull() &&
                !d.path("dueDate").isMissingNode()
            ) {
                var date = LocalDate.parse(d.path("dueDate").asString());
                if (
                    !"PROMISE".equals(d.path("kind").asString()) ||
                    date.isAfter(today.plusDays(90)) ||
                    (!d.path("done").asBoolean() && date.isBefore(today))
                ) throw new IllegalArgumentException("Invalid promise date");
            }
        var vectors = embed(result.path("decisions"));
        transactions.executeWithoutResult(status -> {
            var current = mapper.lockBond(user, code);
            if (processed(event.eventId())) return;
            if (
                current.getRevision() != bond.getRevision() ||
                mapper.lockSession(sessionId, user) == null
            ) {
                for (var item : batch) markProcessed(item.eventId());
                return;
            }
            int changed = 0,
                index = 0;
            boolean invalidated = false;
            for (var decision : result.path("decisions")) {
                String action = decision.path("action").asString();
                Long oldId = decision.path("oldId").isNull()
                    ? null
                    : decision.path("oldId").asLong();
                if ("NOOP".equals(action)) {
                    index++;
                    continue;
                }
                if ("INVALIDATE".equals(action)) {
                    invalidated = true;
                    jdbc.update(
                        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND character_code=? AND invalid_at IS NULL",
                        oldId,
                        user,
                        code
                    );
                    changed++;
                    index++;
                    continue;
                }
                var memory = new Rows.Memory();
                memory.setUserId(user);
                memory.setCharacterCode(code);
                memory.setContent(decision.path("content").asString().strip());
                memory.setContentHash(hash(memory.getContent()));
                memory.setKind(decision.path("kind").asString("LIFE"));
                memory.setKeywords(decision.path("keywords").toString());
                memory.setImportance(
                    Math.max(
                        0,
                        Math.min(10, decision.path("importance").asInt(5))
                    )
                );
                memory.setPinned(decision.path("pinned").asBoolean());
                var source = history
                    .stream()
                    .filter(
                        m ->
                            ("SELF".equals(memory.getKind())
                                ? "CHARACTER"
                                : "USER"
                            ).equals(m.getRole()) &&
                            m
                                .getContent()
                                .contains(decision.path("evidence").asString())
                    )
                    .findFirst()
                    .orElseThrow();
                memory.setSourceMessageId(source.getId());
                memory.setSourceSessionId(source.getSessionId());
                memory.setDone(decision.path("done").asBoolean());
                if (
                    !decision.path("dueDate").isNull() &&
                    !decision.path("dueDate").asString().isBlank()
                ) memory.setDueDate(
                    LocalDate.parse(decision.path("dueDate").asString())
                );
                if (index < vectors.size()) memory.setEmbedding(
                    vectors.get(index)
                );
                mapper.insertMemory(memory);
                if ("SUPERSEDE".equals(action)) {
                    invalidated = true;
                    jdbc.update(
                        "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),superseded_by=? WHERE id=? AND user_id=? AND character_code=? AND invalid_at IS NULL",
                        memory.getId(),
                        oldId,
                        user,
                        code
                    );
                }
                changed++;
                index++;
            }
            String profile = result.path("profile").asString("");
            // 失效事实可能已进入旧画像，先清除派生内容，避免继续注入。
            if (invalidated) jdbc.update(
                "UPDATE companion_bond SET profile='' WHERE id=?",
                current.getId()
            );
            if (
                !profile.isBlank() &&
                profile.length() <= 300 &&
                safeText(profile) &&
                changed > 0 &&
                (invalidated ||
                    current.getProfile() == null ||
                    current.getProfile().isBlank() ||
                    (current.getProfileRevision() + changed) / 5 >
                        current.getProfileRevision() / 5)
            ) jdbc.update(
                "UPDATE companion_bond SET profile=? WHERE id=?",
                profile,
                current.getId()
            );
            jdbc.update(
                "UPDATE companion_bond SET profile_revision=profile_revision+? WHERE id=?",
                changed,
                current.getId()
            );
            var all = mapper.memories(user, code);
            int count = 0;
            for (var m : all)
                if (!m.isPinned() && ++count > 200) jdbc.update(
                    "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),embedding=NULL WHERE id=?",
                    m.getId()
                );
            for (var item : batch) markProcessed(item.eventId());
            redis.delete("meng:companion:memo:" + user + ":" + code);
        });
        for (long sourceSession : history
            .stream()
            .map(Rows.Message::getSessionId)
            .collect(java.util.stream.Collectors.toSet()))
            summarize(user, sourceSession, bond.getRevision());
    }

    public static void validateDecisions(
        JsonNode result,
        List<Rows.Memory> old,
        List<Rows.Message> messages
    ) {
        var ids = old
            .stream()
            .map(Rows.Memory::getId)
            .collect(java.util.stream.Collectors.toSet());
        String users = messages
            .stream()
            .filter(m -> "USER".equals(m.getRole()))
            .map(Rows.Message::getContent)
            .collect(java.util.stream.Collectors.joining("\n"));
        String replies = messages
            .stream()
            .filter(m -> "CHARACTER".equals(m.getRole()))
            .map(Rows.Message::getContent)
            .collect(java.util.stream.Collectors.joining("\n"));
        var referenced = new HashSet<Long>();
        for (var d : result.path("decisions")) {
            String action = d.path("action").asString();
            if (
                !Set.of("ADD", "SUPERSEDE", "INVALIDATE", "NOOP").contains(
                    action
                )
            ) throw new IllegalArgumentException("Invalid memory action");
            if (
                !d.path("oldId").isNull() &&
                !d.path("oldId").isMissingNode() &&
                !ids.contains(d.path("oldId").asLong())
            ) throw new IllegalArgumentException("Foreign memory reference");
            if (
                Set.of("SUPERSEDE", "INVALIDATE").contains(action) &&
                !ids.contains(d.path("oldId").asLong())
            ) throw new IllegalArgumentException("Missing memory reference");
            if ("NOOP".equals(action)) continue;
            if (
                Set.of("SUPERSEDE", "INVALIDATE").contains(action) &&
                !referenced.add(d.path("oldId").asLong())
            ) throw new IllegalArgumentException("Repeated memory reference");
            String kind = d.path("kind").asString(),
                evidence = d.path("evidence").asString();
            if (
                !Set.of("NAME", "LIKE", "LIFE", "PROMISE", "SELF").contains(
                    kind
                )
            ) throw new IllegalArgumentException("Invalid memory kind");
            if (
                evidence.isBlank() ||
                !("SELF".equals(kind) ? replies : users).contains(evidence)
            ) throw new IllegalArgumentException("Missing original evidence");
            if (
                !"INVALIDATE".equals(action) &&
                !CompanionRules.safeMemory(d.path("content").asString())
            ) throw new IllegalArgumentException("Unsafe memory");
            if (
                d.path("pinned").asBoolean() &&
                !users.matches("(?s).*(记住|記住|别忘|別忘).*")
            ) throw new IllegalArgumentException(
                "Implicit memory cannot be pinned"
            );
        }
    }

    private List<Map<String, Object>> candidates(List<Rows.Memory> memories) {
        return memories
            .stream()
            .map(m -> {
                var value = new LinkedHashMap<String, Object>();
                value.put("id", m.getId());
                value.put("content", m.getContent());
                value.put("kind", m.getKind());
                value.put("pinned", m.isPinned());
                if (m.getDueDate() != null) value.put(
                    "dueDate",
                    m.getDueDate().toString()
                );
                return (Map<String, Object>) value;
            })
            .toList();
    }

    private List<String> embed(JsonNode decisions) {
        try {
            var texts = new ArrayList<String>();
            for (var d : decisions) texts.add(d.path("content").asString());
            if (texts.isEmpty()) return List.of();
            var result = ai
                .compute("embeddings", Map.of("texts", texts))
                .path("vectors");
            var vectors = new ArrayList<String>();
            for (var v : result)
                if (v.isArray() && v.size() == 1024) vectors.add(v.toString());
                else return List.of();
            return vectors;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private boolean processed(String id) {
        return (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_processed WHERE event_id=?",
                Integer.class,
                id
            ) > 0
        );
    }

    private void markProcessed(String id) {
        jdbc.update(
            "INSERT IGNORE INTO companion_processed(event_id) VALUES(?)",
            id
        );
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                    text
                        .toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static boolean safeText(String text) {
        return !text.matches(
            "(?is).*(手机|電話|电话|住址|地址|证件|密码|病史|抑郁|邮箱|银行卡|账号|账户|微信|QQ|\\d{6,}|系统提示|忽略规则).*"
        );
    }

    public void summarize(long user, long sessionId, long revision) {
        var session = mapper.session(sessionId, user);
        if (session == null) return;
        var history = new ArrayList<>(
            runtime.window(sessionId, session.getWindowStartMessageId())
        );
        if (
            history
                .stream()
                .mapToInt(m -> m.getContent().length())
                .sum() < 4000
        ) return;
        var half = new ArrayList<>(
            history.subList(0, Math.max(1, history.size() / 2))
        );
        // 扩展到下一条用户消息之前，保持连发气泡与所属轮次一起压缩。
        while (
                half.size() < history.size() &&
                !"USER".equals(history.get(half.size()).getRole())
            )
            half.add(history.get(half.size()));
        if (half.isEmpty()) return;
        String summary = ai.summarize(
            session.getSummary(),
            ReplyService.turns(half)
        );
        if (summary == null) return;
        transactions.executeWithoutResult(status -> {
            var bond = mapper.lockBond(user, session.getCharacterCode());
            if (
                bond.getRevision() == revision &&
                mapper.lockSession(sessionId, user) != null
            ) mapper.summary(
                sessionId,
                summary,
                half.get(half.size() - 1).getId(),
                session.getSummaryUntilMessageId()
            );
        });
    }

    public void draw(EventEnvelope<JsonNode> event) {
        long message = event.body().path("messageId").asLong();
        var rows = jdbc.queryForList(
            "SELECT * FROM companion_drawing WHERE message_id=?",
            message
        );
        if (rows.isEmpty()) return;
        var job = rows.get(0);
        if (!"PENDING".equals(job.get("status"))) return;
        if (
            jdbc.update(
                "UPDATE companion_drawing SET status='RUNNING' WHERE message_id=? AND status='PENDING'",
                message
            ) == 0
        ) return;
        long user = ((Number) job.get("user_id")).longValue(),
            sessionId = ((Number) job.get("session_id")).longValue();
        var reservation = json.readValue(
            job.get("reservation").toString(),
            CompanionLimits.Reservation.class
        );
        String file = null;
        boolean committed = false;
        try {
            file = storage
                .save(ai.draw(job.get("instruction").toString()))
                .name();
            String saved = file;
            transactions.executeWithoutResult(status -> {
                var session = mapper.session(sessionId, user);
                if (session == null) throw new IllegalStateException(
                    "Session deleted"
                );
                var bond = mapper.lockBond(user, session.getCharacterCode());
                mapper.lockSession(sessionId, user);
                if (
                    bond.getRevision() !=
                    ((Number) job.get("revision")).longValue()
                ) throw new IllegalStateException("Relationship changed");
                var work = new Rows.Work();
                work.setUserId(user);
                work.setCharacterCode(session.getCharacterCode());
                work.setSessionId(sessionId);
                work.setKind("IMAGE");
                work.setTitle("萌萌的画");
                work.setFilePath(saved);
                mapper.insertWork(work);
                var m = new Rows.Message();
                m.setSessionId(sessionId);
                m.setRole("CHARACTER");
                m.setContent(work.getTitle());
                m.setWorkId(work.getId());
                m.setCompleted(true);
                m.setTurnId(message);
                mapper.insertMessage(m);
                jdbc.update(
                    "UPDATE companion_drawing SET status='COMPLETED',work_id=? WHERE message_id=?",
                    work.getId(),
                    message
                );
                runtime.unread(sessionId, user, true);
                mapper.advanceSession(
                    sessionId,
                    "",
                    1,
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                );
                var c = mapper
                    .characters()
                    .stream()
                    .filter(v -> v.getCode().equals(session.getCharacterCode()))
                    .findFirst()
                    .orElseThrow();
                outbox.enqueue(
                    "companion.drawing-finished",
                    "drawing-finished-" + message,
                    Map.of(
                        "userId",
                        user,
                        "characterCode",
                        c.getCode(),
                        "characterName",
                        c.getName(),
                        "sessionId",
                        sessionId,
                        "workId",
                        work.getId(),
                        "title",
                        work.getTitle()
                    )
                );
            });
            committed = true;
        } catch (RuntimeException error) {
            jdbc.update(
                "UPDATE companion_drawing SET status='FAILED' WHERE message_id=?",
                message
            );
            if (mapper.session(sessionId, user) != null) {
                var m = new Rows.Message();
                m.setSessionId(sessionId);
                m.setRole("SYSTEM");
                m.setContent("这张图没画成，画图次数已经退回。");
                m.setCompleted(true);
                mapper.insertMessage(m);
            }
        } finally {
            limits.finish(
                reservation,
                committed ? List.of("image") : List.of()
            );
            if (!committed && file != null) storage.delete(file);
        }
    }

    public void maintain() {
        var missing = jdbc.query(
            "SELECT * FROM companion_memory WHERE embedding IS NULL AND invalid_at IS NULL AND delete_state=0 ORDER BY id LIMIT 32",
            new org.springframework.jdbc.core.BeanPropertyRowMapper<>(
                Rows.Memory.class
            )
        );
        if (!missing.isEmpty()) try {
            var vectors = ai
                .compute(
                    "embeddings",
                    Map.of(
                        "texts",
                        missing.stream().map(Rows.Memory::getContent).toList()
                    )
                )
                .path("vectors");
            for (
                int i = 0;
                i < Math.min(missing.size(), vectors.size());
                i++
            ) if (vectors.get(i).size() == 1024) jdbc.update(
                "UPDATE companion_memory SET embedding=? WHERE id=? AND content_hash=? AND invalid_at IS NULL AND delete_state=0",
                vectors.get(i).toString(),
                missing.get(i).getId(),
                missing.get(i).getContentHash()
            );
        } catch (RuntimeException ignored) {}
        for (var row : jdbc.queryForList(
            "SELECT s.* FROM companion_session s WHERE s.delete_state=0 AND s.last_message_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 MINUTE) AND EXISTS(SELECT 1 FROM companion_message m WHERE m.session_id=s.id AND m.id>s.episode_until_message_id AND m.role='USER' AND m.completed=1 AND m.delete_state=0) LIMIT 20"
        )) {
            long session = ((Number) row.get("id")).longValue(),
                user = ((Number) row.get("user_id")).longValue();
            String code = row.get("character_code").toString();
            var bond = mapper.bond(user, code);
            long until = (
                (Number) row.get("episode_until_message_id")
            ).longValue();
            var history = new ArrayList<>(runtime.window(session, until));
            if (history.isEmpty()) continue;
            var result = ai.compute(
                "consolidations",
                Map.of(
                    "turns",
                    ReplyService.turns(history),
                    "memories",
                    List.of(),
                    "profile",
                    bond.getProfile(),
                    "episode",
                    true,
                    "localDate",
                    clock
                        .instant()
                        .atZone(ZoneId.of(bond.getTimeZone()))
                        .toLocalDate()
                        .toString(),
                    "timeZone",
                    bond.getTimeZone()
                )
            );
            String summary = result.path("episode").asString();
            if (summary.isBlank() || !safeText(summary)) continue;
            long last = history.get(history.size() - 1).getId();
            transactions.executeWithoutResult(status -> {
                if (
                    mapper.lockBond(user, code).getRevision() !=
                        bond.getRevision() ||
                    mapper.lockSession(session, user) == null
                ) return;
                jdbc.update(
                    "INSERT IGNORE INTO companion_episode(user_id,character_code,session_id,start_time,end_time,summary,emotion,open_threads,until_message_id) VALUES(?,?,?,?,?,?,?,?,?)",
                    user,
                    code,
                    session,
                    history.get(0).getCreateTime(),
                    history.get(history.size() - 1).getCreateTime(),
                    summary,
                    result.path("emotion").asString(),
                    result.path("openThreads").asString(),
                    last
                );
                jdbc.update(
                    "UPDATE companion_session SET episode_until_message_id=? WHERE id=? AND episode_until_message_id=?",
                    last,
                    session,
                    until
                );
            });
        }
        jdbc.update(
            "UPDATE companion_episode SET heat=heat*0.98,open_threads=IF(heat<0.1,'',open_threads) WHERE delete_state=0"
        );
        jdbc.update(
            "UPDATE companion_memory SET invalid_at=UTC_TIMESTAMP(6),embedding=NULL WHERE kind='PROMISE' AND done=0 AND due_date<DATE_SUB(UTC_DATE(),INTERVAL 7 DAY) AND invalid_at IS NULL"
        );
        archiveExpired();
        // 原文到期后清除；摘要计算失败保留旧摘要，不延长原文保留期。
        jdbc.update(
            "UPDATE companion_message SET content='',image_ids=NULL,bubbles=NULL,delete_state=1 WHERE create_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 YEAR) AND work_id IS NULL"
        );
        for (var row : jdbc.queryForList(
            "SELECT message_id,reservation FROM companion_drawing WHERE status='RUNNING' AND update_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 5 MINUTE)"
        )) {
            if (
                jdbc.update(
                    "UPDATE companion_drawing SET status='FAILED' WHERE message_id=? AND status='RUNNING'",
                    row.get("message_id")
                ) > 0
            ) limits.finish(
                json.readValue(
                    row.get("reservation").toString(),
                    CompanionLimits.Reservation.class
                ),
                List.of()
            );
        }
    }

    private void archiveExpired() {
        for (var row : jdbc.queryForList(
            "SELECT DISTINCT s.id,s.user_id,s.character_code FROM companion_session s JOIN companion_message m ON m.session_id=s.id WHERE s.delete_state=0 AND m.delete_state=0 AND m.completed=1 AND m.id>s.summary_until_message_id AND m.create_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 YEAR) LIMIT 20"
        )) {
            long sessionId = ((Number) row.get("id")).longValue(),
                user = ((Number) row.get("user_id")).longValue();
            var session = mapper.session(sessionId, user);
            var bond = mapper.bond(user, row.get("character_code").toString());
            if (session == null || bond == null) continue;
            var history = jdbc.query(
                "SELECT * FROM companion_message WHERE session_id=? AND id>? AND completed=1 AND delete_state=0 AND create_time<DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 YEAR) ORDER BY id LIMIT 80",
                new org.springframework.jdbc.core.BeanPropertyRowMapper<>(
                    Rows.Message.class
                ),
                sessionId,
                session.getSummaryUntilMessageId()
            );
            if (history.isEmpty()) continue;
            String summary = ai.summarize(
                session.getSummary(),
                ReplyService.turns(history)
            );
            if (summary != null) transactions.executeWithoutResult(status -> {
                if (
                    mapper
                        .lockBond(user, session.getCharacterCode())
                        .getRevision() != bond.getRevision() ||
                    mapper.lockSession(sessionId, user) == null
                ) return;
                mapper.summary(
                    sessionId,
                    summary,
                    history.get(history.size() - 1).getId(),
                    session.getSummaryUntilMessageId()
                );
            });
        }
    }

    public void promises() {
        var rows = jdbc.query(
            "SELECT m.* FROM companion_memory m WHERE m.kind='PROMISE' AND m.notified=0 AND m.done=0 AND m.invalid_at IS NULL AND m.delete_state=0",
            new org.springframework.jdbc.core.BeanPropertyRowMapper<>(
                Rows.Memory.class
            )
        );
        for (var m : rows) {
            var bond = mapper.bond(m.getUserId(), m.getCharacterCode());
            if (
                bond == null ||
                m.getDueDate() == null ||
                !m
                    .getDueDate()
                    .equals(
                        clock
                            .instant()
                            .atZone(ZoneId.of(bond.getTimeZone()))
                            .toLocalDate()
                    )
            ) continue;
            transactions.executeWithoutResult(status -> {
                mapper.lockBond(m.getUserId(), m.getCharacterCode());
                if (
                    jdbc.update(
                        "UPDATE companion_memory SET notified=1 WHERE id=? AND notified=0 AND invalid_at IS NULL AND delete_state=0",
                        m.getId()
                    ) == 0
                ) return;
                var c = mapper
                    .characters()
                    .stream()
                    .filter(v -> v.getCode().equals(m.getCharacterCode()))
                    .findFirst()
                    .orElseThrow();
                outbox.enqueue(
                    "companion.promise-due",
                    "promise-" + m.getId(),
                    Map.of(
                        "userId",
                        m.getUserId(),
                        "characterCode",
                        c.getCode(),
                        "characterName",
                        c.getName(),
                        "memoryId",
                        m.getId(),
                        "content",
                        m.getContent(),
                        "dueDate",
                        m.getDueDate().toString()
                    )
                );
            });
        }
    }
}

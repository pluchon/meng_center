package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 模型计算在事务外进行，提交时重新锁关系并校验 revision；过期结果不能恢复已删除的记忆。
@Service
public class CompanionBackground {

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
    private CompanionRuntimeV2 v2;

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
        var position = runtime.outboxRows(event.eventId());
        if (!position.isEmpty()) {
            long start = ((Number) position.get(0).get("id")).longValue();
            for (var row : runtime.followingTurnEvents(start, start + 32)) {
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
                runtime.turnMessages(
                    item.body().path("messageId").asLong(),
                    item.body().path("sessionId").asLong()
                )
            );
        if (history.isEmpty()) {
            for (var item : batch) markProcessed(item.eventId());
            return;
        }
        // 窗口整理不依赖长期记忆提取成功；先处理轮后容量，再执行可独立失败的记忆任务。
        for (long sourceSession : history
            .stream()
            .map(Rows.Message::getSessionId)
            .collect(java.util.stream.Collectors.toSet()))
            summarize(user, sourceSession, bond.getRevision());
        var old = new ArrayList<>(mapper.memories(user, code));
        String query = history
            .stream()
            .filter(m -> "USER".equals(m.getRole()))
            .map(Rows.Message::getContent)
            .collect(java.util.stream.Collectors.joining(" "));
        JsonNode queryVector = null;
        String queryVersion = "";
        if (!old.isEmpty()) try {
            var encoded = ai.compute(
                "embeddings",
                Map.of(
                    "texts",
                    List.of(query.substring(0, Math.min(1000, query.length()))),
                    "textType",
                    "query"
                )
            );
            queryVector = encoded.path("vectors").get(0);
            queryVersion = embeddingVersion(encoded);
        } catch (RuntimeException ignored) {}
        final JsonNode vector = queryVector;
        final String vectorVersion = queryVersion;
        old.sort(
            Comparator.comparingDouble((Rows.Memory m) ->
                vector != null &&
                !vectorVersion.isBlank() &&
                vectorVersion.equals(m.getEmbeddingVersion()) &&
                m.getEmbedding() != null
                    ? CompanionContext.cosine(
                          vector,
                          json.readTree(m.getEmbedding())
                      )
                    : m.getImportance()
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
                    runtime.invalidateMemory(oldId, user, code);
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
                if (
                    index < vectors.values().size() &&
                    vectors.values().get(index) != null
                ) {
                    memory.setEmbedding(vectors.values().get(index));
                    memory.setEmbeddingVersion(vectors.version());
                }
                mapper.insertMemory(memory);
                if ("SUPERSEDE".equals(action)) {
                    invalidated = true;
                    runtime.supersedeMemory(memory.getId(), oldId, user, code);
                }
                changed++;
                index++;
            }
            String profile = result.path("profile").asString("");
            // 失效事实可能已进入旧画像，先清除派生内容，避免继续注入。
            if (invalidated) runtime.clearProfileById(current.getId());
            if (
                !profile.isBlank() &&
                profile.length() <= 300 &&
                changed > 0 &&
                (invalidated ||
                    current.getProfile() == null ||
                    current.getProfile().isBlank() ||
                    (current.getProfileRevision() + changed) / 5 >
                        current.getProfileRevision() / 5)
            ) runtime.writeProfile(profile, current.getId());
            runtime.advanceProfileRevision(changed, current.getId());
            var all = mapper.memories(user, code);
            int count = 0;
            for (var m : all)
                if (!m.isPinned() && ++count > 200) runtime.evictMemory(
                    m.getId()
                );
            for (var item : batch) markProcessed(item.eventId());
            redis.delete("meng:companion:memo:" + user);
        });
    }

    public static void validateDecisions(
        JsonNode result,
        List<Rows.Memory> old,
        List<Rows.Message> messages
    ) {
        if (
            !result.path("reviewed").asBoolean()
        ) throw new IllegalArgumentException("Missing semantic review");
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
                !Set.of("NAME", "LIKE", "LIFE", "DATE", "SELF").contains(kind)
            ) throw new IllegalArgumentException("Invalid memory kind");
            if (
                evidence.isBlank() ||
                messages
                    .stream()
                    .noneMatch(
                        m ->
                            ("SELF".equals(kind) ? "CHARACTER" : "USER").equals(
                                m.getRole()
                            ) && m.getContent().contains(evidence)
                    )
            ) throw new IllegalArgumentException("Missing original evidence");
            if (
                !"INVALIDATE".equals(action) &&
                !CompanionRules.validMemory(d.path("content").asString())
            ) throw new IllegalArgumentException("Unsafe memory");
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
                return (Map<String, Object>) value;
            })
            .toList();
    }

    private record MemoryVectors(List<String> values, String version) {}

    private static String embeddingVersion(JsonNode value) {
        String version = value.path("modelVersion").asString("");
        if (
            version.isBlank() || version.length() > 160
        ) throw new IllegalArgumentException("缺少向量模型版本");
        return version;
    }

    private MemoryVectors embed(JsonNode decisions) {
        try {
            var texts = new ArrayList<String>();
            var indexes = new ArrayList<Integer>();
            for (int index = 0; index < decisions.size(); index++) {
                var decision = decisions.get(index);
                if (
                    Set.of("ADD", "SUPERSEDE").contains(
                        decision.path("action").asString()
                    )
                ) {
                    texts.add(decision.path("content").asString());
                    indexes.add(index);
                }
            }
            if (texts.isEmpty()) return new MemoryVectors(List.of(), "");
            var result = ai.compute(
                "embeddings",
                Map.of("textType", "document", "texts", texts)
            );
            var values = result.path("vectors");
            if (values.size() != texts.size()) return new MemoryVectors(
                List.of(),
                ""
            );
            var aligned = new ArrayList<String>(
                java.util.Collections.nCopies(decisions.size(), null)
            );
            for (int i = 0; i < values.size(); i++) {
                var vector = values.get(i);
                if (
                    !vector.isArray() || vector.size() != 1024
                ) return new MemoryVectors(List.of(), "");
                aligned.set(indexes.get(i), vector.toString());
            }
            return new MemoryVectors(aligned, embeddingVersion(result));
        } catch (RuntimeException ignored) {
            return new MemoryVectors(List.of(), "");
        }
    }

    private boolean processed(String id) {
        return runtime.processedEventCount(id) > 0;
    }

    private void markProcessed(String id) {
        runtime.markProcessed(id);
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

    public void summarize(long user, long sessionId, long revision) {
        var session = mapper.session(sessionId, user);
        if (session == null) return;
        var history = new ArrayList<>(
            runtime.window(sessionId, session.getWindowStartMessageId())
        );
        int total = history
            .stream()
            .mapToInt(m -> CompanionRuntimeV2.tokens(m.getContent()))
            .sum();
        boolean idle =
            session.getLastMessageTime() != null &&
            session
                .getLastMessageTime()
                .plusMinutes(30)
                .compareTo(
                    LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
                ) <= 0;
        boolean pressured = total >= 600000 || contextPressure(history);
        if (!pressured && (!idle || total <= 8000)) return;
        int keep = pressured ? total / 2 : 8000;
        int accumulated = 0,
            boundary = history.size();
        while (boundary > 0 && accumulated < keep)
            accumulated += CompanionRuntimeV2.tokens(
                history.get(--boundary).getContent()
            );
        while (boundary > 0 && !"USER".equals(history.get(boundary).getRole()))
            boundary--;
        var half = new ArrayList<>(history.subList(0, boundary));
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
                mapper.lockSession(sessionId, user) != null &&
                Objects.equals(
                    mapper.session(sessionId, user).getLastMessageTime(),
                    session.getLastMessageTime()
                )
            ) mapper.summary(
                sessionId,
                summary,
                half.get(half.size() - 1).getId(),
                session.getSummaryUntilMessageId()
            );
        });
    }

    private boolean contextPressure(List<Rows.Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            var message = history.get(i);
            if (
                !"CHARACTER".equals(message.getRole()) ||
                message.getMetrics() == null
            ) continue;
            try {
                return json
                    .readTree(message.getMetrics())
                    .path("layers")
                    .path("contextBudget")
                    .path("compactAfterRound")
                    .asBoolean();
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    public void maintain() {
        try {
            var metadata = ai.compute(
                "embeddings",
                Map.of("textType", "document", "texts", List.of())
            );
            String expectedVersion = embeddingVersion(metadata);
            var missing = runtime.missingMemoryVectors(expectedVersion);
            if (!missing.isEmpty()) {
                var result = ai.compute(
                    "embeddings",
                    Map.of(
                        "textType",
                        "document",
                        "texts",
                        missing.stream().map(Rows.Memory::getContent).toList()
                    )
                );
                String actualVersion = embeddingVersion(result);
                var vectors = result.path("vectors");
                if (
                    vectors.size() != missing.size()
                ) throw new IllegalArgumentException("向量数量不匹配");
                for (int i = 0; i < missing.size(); i++) {
                    var vector = vectors.get(i);
                    if (
                        vector.isArray() &&
                        vector.size() == 1024 &&
                        runtime.writeMemoryVector(
                            vector.toString(),
                            missing.get(i).getId(),
                            missing.get(i).getContentHash(),
                            actualVersion
                        ) > 0
                    ) redis.delete(
                        "meng:companion:memo:" + missing.get(i).getUserId()
                    );
                }
            }
        } catch (RuntimeException ignored) {}
        for (var row : runtime.idleSessions()) {
            long session = ((Number) row.get("id")).longValue(),
                user = ((Number) row.get("user_id")).longValue();
            String code = row.get("character_code").toString();
            var bond = mapper.bond(user, code);
            summarize(user, session, bond.getRevision());
            transactions.executeWithoutResult(status -> {
                var live = mapper.lockSession(session, user);
                if (
                    live != null &&
                    mapper.lockBond(user, code).getRevision() ==
                        bond.getRevision()
                ) v2.snapshot(live, bond, true);
            });
        }
        archiveExpired();
        // 原文到期后清除；摘要计算失败保留旧摘要，不延长原文保留期。
        runtime.purgeExpiredMessages();
    }

    private void archiveExpired() {
        for (var row : runtime.expiredSessions()) {
            long sessionId = ((Number) row.get("id")).longValue(),
                user = ((Number) row.get("user_id")).longValue();
            var session = mapper.session(sessionId, user);
            var bond = mapper.bond(user, row.get("character_code").toString());
            if (session == null || bond == null) continue;
            var history = runtime.expiredHistory(
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
}

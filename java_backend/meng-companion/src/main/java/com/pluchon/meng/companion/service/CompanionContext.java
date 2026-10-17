package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 只组装本人和当前角色的数据；向量服务不可用时退回本地重要度与关键词排序。
@Service
public class CompanionContext {

    @Autowired
    private CompanionMapper mapper;

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private AiClient ai;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public JsonNode card(long user, String code) {
        String card = runtime.card(code, user);
        if (
            card == null
        ) throw new com.pluchon.meng.common.core.error.BusinessException(
            CompanionError.AI_UNAVAILABLE
        );
        return json.readTree(card);
    }

    public record Memories(
        List<String> stable,
        List<Map<String, Object>> items,
        List<Map<String, Object>> recalled,
        List<Long> used,
        List<Map<String, Object>> due,
        Map<String, Object> shared,
        int tokens
    ) {}

    public Memories memories(
        long user,
        String code,
        String query,
        String zone,
        String turn
    ) {
        int[] usedTokens = { 0 };
        java.util.function.Function<Map<String, Object>, JsonNode> compute =
            data -> {
                var payload = new LinkedHashMap<String, Object>(data);
                payload.put("userId", user);
                payload.put("character", code);
                payload.put("timeZone", zone);
                payload.put("turnId", turn);
                var response = ai.compute("embeddings", payload);
                usedTokens[0] += Math.max(0, response.path("tokens").asInt());
                return response;
            };
        String cacheKey = "meng:companion:memo:" + user + ":" + code;
        var all = new ArrayList<Rows.Memory>();
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) all.addAll(
                Arrays.asList(json.readValue(cached, Rows.Memory[].class))
            );
            else {
                all.addAll(mapper.memories(user, code));
                redis
                    .opsForValue()
                    .set(
                        cacheKey,
                        json.writeValueAsString(all),
                        Duration.ofHours(1)
                    );
            }
        } catch (RuntimeException ignored) {
            all = new ArrayList<>(mapper.memories(user, code));
        }
        var date = clock.instant().atZone(ZoneId.of(zone)).toLocalDate();
        var due = all
            .stream()
            .filter(
                m ->
                    "PROMISE".equals(m.getKind()) &&
                    !m.isDone() &&
                    m.getDueDate() != null &&
                    !m.getDueDate().isAfter(date) &&
                    !m.getDueDate().isBefore(date.minusDays(2))
            )
            .limit(1)
            .map(this::memory)
            .toList();
        var stable = new ArrayList<Rows.Memory>();
        var other = new ArrayList<Rows.Memory>();
        for (var m : all) {
            if (m.isPinned() || "SELF".equals(m.getKind())) stable.add(m);
            else if (!"PROMISE".equals(m.getKind())) other.add(m);
        }
        boolean fits =
            stable
                .stream()
                .mapToInt(m -> m.getContent().length())
                .sum() +
                other
                    .stream()
                    .mapToInt(m -> m.getContent().length())
                    .sum() <=
            800;
        List<Rows.Memory> chosen;
        if (fits) chosen = other;
        else {
            JsonNode vector = null;
            try {
                vector = compute
                    .apply(Map.of("texts", List.of(query)))
                    .path("vectors")
                    .get(0);
            } catch (RuntimeException ignored) {}
            final JsonNode q = vector;
            if (query.contains("上个月") || query.contains("上個月")) {
                var first = date.withDayOfMonth(1).minusMonths(1);
                other.removeIf(
                    m ->
                        m.getValidFrom() != null &&
                        (m.getValidFrom().toLocalDate().isBefore(first) ||
                            !m
                                .getValidFrom()
                                .toLocalDate()
                                .isBefore(first.plusMonths(1)))
                );
            }
            other.sort(
                Comparator.comparingDouble((Rows.Memory m) ->
                    score(m, query, q)
                ).reversed()
            );
            chosen = new ArrayList<>(
                other.subList(0, Math.min(20, other.size()))
            );
        }
        var candidates = chosen
            .stream()
            .map(this::memory)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        var selected = new ArrayList<>(chosen);
        if (!fits && !chosen.isEmpty()) {
            try {
                var ranked = compute
                    .apply(
                        Map.of(
                            "query",
                            query,
                            "candidates",
                            chosen
                                .stream()
                                .map(Rows.Memory::getContent)
                                .toList()
                        )
                    )
                    .path("ranked");
                var order = new ArrayList<Rows.Memory>();
                var seen = new HashSet<Integer>();
                for (var i : ranked)
                    if (
                        i.asInt() >= 0 &&
                        i.asInt() < chosen.size() &&
                        seen.add(i.asInt())
                    ) order.add(chosen.get(i.asInt()));
                if (!order.isEmpty()) selected = order;
            } catch (RuntimeException ignored) {}
            selected = new ArrayList<>(
                selected.subList(0, Math.min(5, selected.size()))
            );
        }
        stable.addAll(selected);
        if (candidates.size() > 15) candidates
            .subList(15, candidates.size())
            .clear();
        // 回忆包可以包含未注入慢变层的候选；只有实际带入的事实才更新使用时间。
        for (var episode : jdbc.queryForList(
            "SELECT id,summary,heat FROM companion_episode WHERE user_id=? AND character_code=? AND delete_state=0 ORDER BY heat DESC,end_time DESC LIMIT 3",
            user,
            code
        )) {
            candidates.add(
                0,
                Map.of(
                    "id",
                    "episode-" + episode.get("id"),
                    "kind",
                    "EPISODE",
                    "content",
                    episode.get("summary")
                )
            );
            jdbc.update(
                "UPDATE companion_episode SET heat=heat+1 WHERE id=? AND user_id=? AND character_code=?",
                episode.get("id"),
                user,
                code
            );
        }
        var oldMessages = jdbc.queryForList(
            "SELECT m.id,m.content FROM companion_message m JOIN companion_session s ON s.id=m.session_id WHERE s.user_id=? AND s.character_code=? AND s.delete_state=0 AND m.delete_state=0 AND m.completed=1 AND m.work_id IS NULL AND m.kind='TEXT' ORDER BY m.id DESC LIMIT 100",
            user,
            code
        );
        oldMessages.sort(
            Comparator.comparingLong((Map<String, Object> m) ->
                query
                    .codePoints()
                    .filter(c -> m.get("content").toString().indexOf(c) >= 0)
                    .count()
            ).reversed()
        );
        for (var m : oldMessages.subList(0, Math.min(2, oldMessages.size())))
            candidates.add(
                Map.of(
                    "id",
                    "message-" + m.get("id"),
                    "kind",
                    "MESSAGE",
                    "content",
                    m.get("content")
                )
            );
        var texts = stable.stream().map(Rows.Memory::getContent).toList();
        return new Memories(
            texts,
            stable.stream().map(this::memory).toList(),
            candidates.subList(0, Math.min(20, candidates.size())),
            stable.stream().map(Rows.Memory::getId).toList(),
            due,
            stable
                .stream()
                .filter(
                    m ->
                        m.getMentionedAt() == null ||
                        m
                            .getMentionedAt()
                            .isBefore(
                                LocalDateTime.ofInstant(
                                    clock.instant(),
                                    ZoneOffset.UTC
                                ).minusDays(3)
                            )
                )
                .filter(
                    m ->
                        query
                            .codePoints()
                            .distinct()
                            .filter(c -> m.getContent().indexOf(c) >= 0)
                            .count() >= 2
                )
                .findFirst()
                .map(this::memory)
                .orElse(Map.of()),
            usedTokens[0]
        );
    }

    private Map<String, Object> memory(Rows.Memory m) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", m.getId());
        value.put("kind", m.getKind());
        value.put("content", m.getContent());
        value.put("pinned", m.isPinned());
        if (m.getDueDate() != null) value.put(
            "dueDate",
            m.getDueDate().toString()
        );
        return value;
    }

    private double score(Rows.Memory memory, String query, JsonNode q) {
        long overlap = query
            .codePoints()
            .distinct()
            .filter(c -> memory.getContent().indexOf(c) >= 0)
            .count();
        double similarity = 0;
        if (q != null && memory.getEmbedding() != null) similarity = cosine(
            q,
            json.readTree(memory.getEmbedding())
        );
        long age =
            memory.getLastUsedTime() == null
                ? 0
                : Math.max(
                      0,
                      Duration.between(
                          memory.getLastUsedTime(),
                          LocalDateTime.ofInstant(
                              clock.instant(),
                              ZoneOffset.UTC
                          )
                      ).toDays()
                  );
        return (
            overlap * 0.3 +
            similarity * 4 +
            memory.getImportance() * 0.1 +
            1d / (1 + age / 30d)
        );
    }

    public static double cosine(JsonNode a, JsonNode b) {
        if (
            !a.isArray() || !b.isArray() || a.size() != b.size() || a.isEmpty()
        ) return 0;
        double sum = 0,
            aa = 0,
            bb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i).asDouble(),
                y = b.get(i).asDouble();
            sum += x * y;
            aa += x * x;
            bb += y * y;
        }
        return aa == 0 || bb == 0 ? 0 : sum / Math.sqrt(aa * bb);
    }

    public List<Map<String, Object>> works(
        long user,
        Rows.Session session,
        Long selected
    ) {
        var works = mapper.works(user, session.getCharacterCode(), 0, 20);
        Rows.Work full =
            selected == null
                ? works
                      .stream()
                      .filter(
                          w ->
                              w.getSessionId().equals(session.getId()) &&
                              "DOCUMENT".equals(w.getKind())
                      )
                      .findFirst()
                      .orElse(null)
                : mapper.work(selected, user);
        if (
            selected != null &&
            (full == null ||
                !session.getCharacterCode().equals(full.getCharacterCode()) ||
                !"DOCUMENT".equals(full.getKind()))
        ) throw new com.pluchon.meng.common.core.error.BusinessException(
            com.pluchon.meng.common.core.error.CommonErrorCode.NOT_FOUND
        );
        var result = new ArrayList<Map<String, Object>>();
        for (var work : works)
            result.add(
                Map.of(
                    "id",
                    work.getId(),
                    "title",
                    work.getTitle(),
                    "summary",
                    work.getBody() == null
                        ? ""
                        : work
                              .getBody()
                              .substring(
                                  0,
                                  Math.min(120, work.getBody().length())
                              )
                )
            );
        if (full != null) {
            long id = full.getId();
            result.removeIf(w -> w.get("id").equals(id));
            result.add(
                Map.of(
                    "id",
                    id,
                    "title",
                    full.getTitle(),
                    "body",
                    full.getBody()
                )
            );
        }
        return result;
    }

    public List<JsonNode> examples(
        Rows.Bond bond,
        String input,
        JsonNode card
    ) {
        return runtime
            .lines(
                bond.getCharacterCode(),
                bond.getLevel(),
                bond.getStage(),
                card
                    .path("lineVersion")
                    .asString(card.path("version").asString())
            )
            .stream()
            .map(json::readTree)
            .sorted(
                Comparator.comparingLong((JsonNode n) ->
                    input
                        .codePoints()
                        .filter(c -> n.path("user").asString().indexOf(c) >= 0)
                        .count()
                ).reversed()
            )
            .limit(3)
            .sorted(Comparator.comparing(n -> n.path("user").asString()))
            .toList();
    }

    public String daily(String code, String zone) {
        var date = clock.instant().atZone(ZoneId.of(zone)).toLocalDate();
        jdbc.update(
            "INSERT IGNORE INTO companion_daily(character_code,date,content) VALUES(?,?,?)",
            code,
            date,
            "今天也在萌部落，想听听大家最近的故事。"
        );
        return jdbc.queryForObject(
            "SELECT content FROM companion_daily WHERE character_code=? AND date=?",
            String.class,
            code,
            date
        );
    }

    public static String zone(String zone) {
        try {
            return zone != null &&
                zone.length() <= 80 &&
                ZoneId.getAvailableZoneIds().contains(zone)
                ? zone
                : "Asia/Taipei";
        } catch (RuntimeException ignored) {
            return "Asia/Taipei";
        }
    }
}

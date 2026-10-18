package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 只组装本人和当前角色的数据；向量用于候选召回，相关性由 AI agent 判断。
@Service
public class CompanionContext {

    @Autowired
    private CompanionMapper mapper;

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private AiClient ai;

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
            other.sort(
                Comparator.comparingDouble((Rows.Memory m) ->
                    score(m, q)
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
        var selected = new ArrayList<Rows.Memory>();
        if (fits) selected.addAll(chosen);
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
                if (!ranked.isArray()) throw new IllegalArgumentException(
                    "Missing agent retrieval selection"
                );
                for (var i : ranked) {
                    if (
                        !i.isIntegralNumber() ||
                        i.asInt() < 0 ||
                        i.asInt() >= chosen.size() ||
                        !seen.add(i.asInt())
                    ) throw new IllegalArgumentException(
                        "Invalid agent retrieval selection"
                    );
                    order.add(chosen.get(i.asInt()));
                }
                selected = order;
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
        for (var episode : runtime.recallEpisodes(user, code)) {
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
            runtime.touchEpisode(
                ((Number) episode.get("id")).longValue(),
                user,
                code
            );
        }
        var oldMessages = runtime.recallMessages(user, code);
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
            Map.of(
                "candidates",
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
                    .limit(20)
                    .map(this::memory)
                    .toList()
            ),
            usedTokens[0]
        );
    }

    private Map<String, Object> memory(Rows.Memory m) {
        var value = new LinkedHashMap<String, Object>();
        value.put("id", m.getId());
        value.put("kind", m.getKind());
        value.put("content", m.getContent());
        value.put("pinned", m.isPinned());
        if (m.getValidFrom() != null) value.put(
            "validFrom",
            m.getValidFrom().toString() + "Z"
        );
        if (m.getDueDate() != null) value.put(
            "dueDate",
            m.getDueDate().toString()
        );
        return value;
    }

    private double score(Rows.Memory memory, JsonNode q) {
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
            similarity * 4 + memory.getImportance() * 0.1 + 1d / (1 + age / 30d)
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

    public List<JsonNode> examples(Rows.Bond bond, JsonNode card) {
        // 这里只限定角色、阶段、档位与版本；场景含义和示例相关性由 AI 判断。
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
            .map(line -> {
                var value = json.createObjectNode();
                value.put("id", line.getId());
                value.put("scene", line.getScene());
                value.set("dialogue", json.readTree(line.getDialogue()));
                return (JsonNode) value;
            })
            .toList();
    }

    // 「今天的她」由后台任务按日期生成；还没生成时不放这一项，不用固定句子顶替。
    public String daily(String code, String zone) {
        var date = clock.instant().atZone(ZoneId.of(zone)).toLocalDate();
        return Objects.requireNonNullElse(runtime.daily(code, date), "");
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

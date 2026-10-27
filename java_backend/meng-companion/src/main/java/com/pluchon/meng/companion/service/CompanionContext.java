package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 只组装本人和当前角色的数据；向量用于候选召回，相关性由原生 rerank 判断。
@Service
public class CompanionContext {

    @Autowired
    private CompanionMemoryCache memoryCache;

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private AiClient ai;

    @Autowired
    private Clock clock;

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
        List<Map<String, Object>> recalled,
        List<Long> used
    ) {}

    public List<Rows.Memory> allMemories(long user, String code) {
        return memoryCache.get(user, code);
    }

    public Memories memories(
        long user,
        String code,
        String query,
        String zone,
        String turn
    ) {
        var all = new ArrayList<>(allMemories(user, code));
        JsonNode vector = null;
        String version = "";
        if (!all.isEmpty()) try {
            var result = ai.compute(
                "embeddings",
                Map.of(
                    "texts",
                    List.of(query),
                    "textType",
                    "query",
                    "userId",
                    user,
                    "character",
                    code,
                    "turnId",
                    turn,
                    "timeZone",
                    zone
                )
            );
            vector = result.path("vectors").get(0);
            version = result.path("modelVersion").asString("");
        } catch (RuntimeException ignored) {}
        final JsonNode q = vector;
        final String targetVersion = version;
        all.sort(
            Comparator.comparingDouble((Rows.Memory m) ->
                score(m, q, targetVersion)
            ).reversed()
        );
        return new Memories(
            all.stream().limit(20).map(this::memory).toList(),
            List.of()
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
        return value;
    }

    private double score(Rows.Memory memory, JsonNode q, String version) {
        double similarity = 0;
        if (
            q != null &&
            !version.isBlank() &&
            version.equals(memory.getEmbeddingVersion()) &&
            memory.getEmbedding() != null
        ) similarity = cosine(q, json.readTree(memory.getEmbedding()));
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

    public List<JsonNode> examples(Rows.Bond bond, JsonNode card) {
        // 所有用户使用同一组示例，Python 按场景固定排序。
        return runtime
            .lines(
                bond.getCharacterCode(),
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

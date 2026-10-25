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

// 只组装本人和当前角色的数据；向量用于候选召回，相关性由原生 rerank 判断。
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
        var all = new ArrayList<>(mapper.memories(user, code));
        var stable = all.stream().filter(m -> m.isPinned() || "SELF".equals(m.getKind()) || "BIT".equals(m.getKind())).toList();
        JsonNode vector = null;
        if (all.size()>20) try {
            vector = ai.compute("embeddings",Map.of("texts",List.of(query),"textType","query")).path("vectors").get(0);
        } catch (RuntimeException ignored) { }
        final JsonNode q = vector;
        all.sort(Comparator.comparingDouble((Rows.Memory m) -> score(m,q)).reversed());
        var candidates = new ArrayList<Map<String,Object>>();
        all.stream().limit(15).map(this::memory).forEach(candidates::add);
        for(var episode:runtime.recallEpisodes(user,code)) {
            var item = new LinkedHashMap<String,Object>();
            item.put("id","episode-"+episode.get("id")); item.put("kind","EPISODE"); item.put("content",episode.get("summary"));
            candidates.add(item);
        }
        runtime.recallMessages(user,code).stream().limit(2).forEach(m -> candidates.add(Map.of(
            "id","message-"+m.get("id"),"kind","MESSAGE","content",m.get("content"))));
        var date = clock.instant().atZone(ZoneId.of(zone)).toLocalDate();
        var due = all.stream().filter(m -> "PROMISE".equals(m.getKind()) && !m.isDone() && m.getDueDate()!=null &&
            !m.getDueDate().isAfter(date) && !m.getDueDate().isBefore(date.minusDays(2))).limit(1).map(this::memory).toList();
        return new Memories(stable.stream().map(Rows.Memory::getContent).toList(),
            stable.stream().map(this::memory).toList(), candidates.subList(0,Math.min(20,candidates.size())),
            stable.stream().map(Rows.Memory::getId).toList(),due,Map.of(),0);
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

    public List<JsonNode> examples(Rows.Bond bond, JsonNode card) {
        // 同一角色、阶段、档位使用整组示例，Python 按场景固定排序。
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

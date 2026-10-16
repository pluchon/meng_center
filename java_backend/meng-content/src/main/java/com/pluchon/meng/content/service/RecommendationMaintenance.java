package com.pluchon.meng.content.service;

import com.pluchon.meng.content.client.AiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

// 推荐训练样本清理与离线排序权重更新
@Service
public class RecommendationMaintenance {

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 纯计算 AI 客户端
    @Autowired
    private AiClient ai;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 排序权重切换事务
    @Autowired
    private TransactionTemplate transactions;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    // 清理到期曝光样本与已发送事件
    public void clean() {
        jdbc.update(
            "DELETE FROM recommend_log WHERE create_time<?",
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30)))
        );
        jdbc.update(
            "DELETE FROM ai_event_outbox WHERE sent=1 AND create_time<?",
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(7)))
        );
    }

    // 样本和留出集达标时事务切换排序权重
    public void train() {
        var rows = jdbc.queryForList(
            "SELECT features,clicked,create_time FROM recommend_log WHERE create_time>=? ORDER BY create_time DESC,id DESC LIMIT 100000",
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30)))
        );
        if (
            rows.size() < 5000 ||
            rows
                .stream()
                .filter(r -> {
                    Object value = r.get("clicked");
                    return value instanceof Boolean b ? b : ((Number) value).intValue() == 1;
                })
                .count() < 200
        ) return;
        var samples = new ArrayList<Map<String, Object>>();
        for (var row : rows) {
            Object clicked = row.get("clicked");
            var time = row.get("create_time");
            LocalDateTime at =
                time instanceof LocalDateTime t ? t : ((java.sql.Timestamp) time).toLocalDateTime();
            samples.add(
                Map.of(
                    "features",
                    json.readValue(
                        row.get("features").toString(),
                        new tools.jackson.core.type.TypeReference<List<Double>>() {}
                    ),
                    "clicked",
                    clicked instanceof Boolean b ? b : ((Number) clicked).intValue() == 1,
                    "occurredAt",
                    at.atOffset(ZoneOffset.UTC).toString()
                )
            );
        }
        Map<String, Object> result;
        try {
            result = ai.train(Map.of("samples", samples));
        } catch (RuntimeException error) {
            return;
        }
        if (!Boolean.TRUE.equals(result.get("eligible"))) return;
        double auc = ((Number) result.get("auc")).doubleValue();
        if (!Double.isFinite(auc)) return;
        transactions.executeWithoutResult(status -> {
            Double previous = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CASE WHEN active=1 THEN auc END),0.5) FROM recommend_ranker",
                Double.class
            );
            if (auc < previous + .01) return;
            var weights = json.convertValue(
                result.get("weights"),
                new tools.jackson.core.type.TypeReference<List<Double>>() {}
            );
            if (weights.size() != 11 || weights.stream().anyMatch(v -> !Double.isFinite(v))) return;
            jdbc.update("UPDATE recommend_ranker SET active=0 WHERE active=1");
            jdbc.update(
                "INSERT INTO recommend_ranker(version,weights,intercept,auc,sample_count,click_count,active) VALUES (?,?,?,?,?,?,1)",
                clock.instant().toEpochMilli(),
                json.writeValueAsString(weights),
                result.get("intercept"),
                auc,
                result.get("sampleCount"),
                result.get("clickCount")
            );
        });
    }
}

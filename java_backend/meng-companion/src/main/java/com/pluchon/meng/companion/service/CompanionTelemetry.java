package com.pluchon.meng.companion.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 只记录数值和低基数标签，不将对话、用户编号或记忆正文写入指标标签。
@Component
public class CompanionTelemetry {

    private final MeterRegistry registry;
    private final Timer latency;

    public CompanionTelemetry(MeterRegistry registry, JdbcTemplate jdbc) {
        this.registry = registry;
        latency = Timer.builder("companion.first.bubble")
            .publishPercentileHistogram()
            .publishPercentiles(0.5, 0.95)
            .register(registry);
        registry.gauge("companion.outbox.pending", jdbc, j ->
            j.queryForObject(
                "SELECT COUNT(*) FROM companion_outbox WHERE status='PENDING'",
                Double.class
            )
        );
        registry.gauge("companion.feedback.today", jdbc, j ->
            j.queryForObject(
                "SELECT COUNT(*) FROM companion_feedback WHERE create_time>=UTC_DATE()",
                Double.class
            )
        );
    }

    public void completed(JsonNode result, long nanos) {
        latency.record(nanos, TimeUnit.NANOSECONDS);
        String label = result.path("label").asString("UNKNOWN");
        registry.counter("companion.rounds", "label", label).increment();
        var metrics = result.path("metrics");
        if (metrics.path("degraded").asBoolean()) registry
            .counter("companion.degraded")
            .increment();
        if (!result.path("billable").asBoolean(true)) registry
            .counter("companion.fallback")
            .increment();
        registry
            .counter("companion.rewrites")
            .increment(Math.max(0, metrics.path("rewrites").asInt()));
        for (var call : metrics.path("calls")) {
            String role = call.path("role").asString("unknown");
            registry
                .counter("companion.tokens.input", "role", role)
                .increment(Math.max(0, call.path("input").asDouble()));
            registry
                .counter("companion.tokens.output", "role", role)
                .increment(Math.max(0, call.path("output").asDouble()));
            registry
                .counter("companion.tokens.cached", "role", role)
                .increment(Math.max(0, call.path("cached").asDouble()));
            registry
                .counter("companion.cost.estimated", "role", role)
                .increment(Math.max(0, call.path("cost").asDouble()));
        }
    }
}

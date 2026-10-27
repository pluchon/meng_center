package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.mapper.RuntimeMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 只记录数值和低基数标签，不将对话、用户编号或记忆正文写入指标标签。
@Component
public class CompanionTelemetry {

    private final MeterRegistry registry;
    private final Timer latency;

    public CompanionTelemetry(MeterRegistry registry, RuntimeMapper runtime) {
        this.registry = registry;
        latency = Timer.builder("companion.first.bubble")
            .publishPercentileHistogram()
            .publishPercentiles(0.5, 0.95)
            .register(registry);
        registry.gauge(
            "companion.outbox.pending",
            runtime,
            RuntimeMapper::pendingOutboxCount
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
        registry.counter("companion.unsupported").increment(Math.max(0, metrics.path("unsupported").asInt()));
        registry.summary("companion.history.tokens").record(Math.max(0, metrics.path("historyTokens").asDouble()));
        for (var tool : metrics.path("tools")) {
            String name = tool.path("name").asString("unknown");
            registry.counter("companion.tools", "tool", name,
                "success", Boolean.toString(tool.path("success").asBoolean()),
                "cached", Boolean.toString(tool.path("cached").asBoolean())).increment();
            registry.timer("companion.tool.latency", "tool", name)
                .record(Math.max(0, tool.path("elapsedMs").asLong()), TimeUnit.MILLISECONDS);
        }
        for (var call : metrics.path("calls")) {
            String role = call.path("purpose").asString("unknown");
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

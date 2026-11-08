package com.pluchon.meng.content.client;

import com.pluchon.meng.content.config.SearchProperties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 搜索、相关推荐这类读路径调用 AI 的熔断：连续失败几次就在冷却期内直接走降级，不让每个请求都等满超时
@Component
public class AiReadCircuit {

    // 连续失败多少次后打开
    private static final int THRESHOLD = 3;

    // 熔断打开的日志
    private static final Logger LOG = LoggerFactory.getLogger(AiReadCircuit.class);

    // 冷却时长沿用搜索的故障冷却配置
    @Autowired
    private SearchProperties properties;

    // 连续失败次数，成功一次清零；冷却结束后的第一次失败会立即再次打开
    private final AtomicInteger failures = new AtomicInteger();

    // 冷却结束的时间点（纳秒）
    private final AtomicLong openUntil = new AtomicLong();

    // 冷却期内直接抛出，调用方按 AI 不可用降级
    public <T> T call(Supplier<T> operation) {
        if (System.nanoTime() < openUntil.get()) throw new IllegalStateException("AI read path cooling down");
        try {
            T result = operation.get();
            failures.set(0);
            return result;
        } catch (RuntimeException failure) {
            if (failures.incrementAndGet() >= THRESHOLD) {
                openUntil.set(System.nanoTime() + properties.failureCooldown().toNanos());
                LOG.warn("AI read path open for {}: {}", properties.failureCooldown(), failure.getClass().getSimpleName());
            }
            throw failure;
        }
    }
}

package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.pluchon.meng.content.client.AiReadCircuit;
import com.pluchon.meng.content.config.SearchProperties;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiReadCircuitTest {

    private AiReadCircuit circuit;
    private final AtomicInteger calls = new AtomicInteger();

    // 其他手工装配服务的测试也用它注入真实熔断
    static AiReadCircuit circuit() {
        var value = new AiReadCircuit();
        ReflectionTestUtils.setField(
            value,
            "properties",
            new SearchProperties("ik_max_word", "ik_smart", Duration.ofMinutes(1))
        );
        return value;
    }

    @BeforeEach
    void setup() {
        circuit = circuit();
    }

    private String failing() {
        calls.incrementAndGet();
        throw new IllegalStateException("timeout");
    }

    @Test
    void opensAfterThreeConsecutiveFailuresAndSkipsTheCall() {
        for (int i = 0; i < 3; i++) assertThrows(IllegalStateException.class, () -> circuit.call(this::failing));
        assertThrows(IllegalStateException.class, () -> circuit.call(this::failing));
        assertEquals(3, calls.get());
    }

    @Test
    void successResetsTheFailureCount() {
        for (int i = 0; i < 2; i++) assertThrows(IllegalStateException.class, () -> circuit.call(this::failing));
        assertEquals("ok", circuit.call(() -> "ok"));
        for (int i = 0; i < 2; i++) assertThrows(IllegalStateException.class, () -> circuit.call(this::failing));
        assertEquals("ok", circuit.call(() -> "ok"));
        assertEquals(4, calls.get());
    }
}

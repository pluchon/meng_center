package com.pluchon.meng.companion;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.companion.client.AiClient;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import com.pluchon.meng.companion.service.CompanionBackground;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

class MemoryVectorVersionTest {

    private final RuntimeMapper runtime = mock(RuntimeMapper.class);
    private final AiClient ai = mock(AiClient.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final JsonMapper json = JsonMapper.builder().build();

    private CompanionBackground background() {
        var value = new CompanionBackground();
        ReflectionTestUtils.setField(value, "runtime", runtime);
        ReflectionTestUtils.setField(value, "ai", ai);
        ReflectionTestUtils.setField(value, "redis", redis);
        return value;
    }

    @Test
    void changedEmbeddingModelIsRebuiltAndStoredWithTheActualVersion() {
        String version = "embedding-model-v2:1024:doc-v1";
        var memory = new Rows.Memory();
        memory.setId(10L);
        memory.setUserId(42L);
        memory.setContent("喜欢蓝色");
        memory.setContentHash("fixture");
        when(runtime.missingMemoryVectors(version)).thenReturn(List.of(memory));
        when(ai.compute(eq("embeddings"), anyMap())).thenAnswer(call -> {
            Map<?, ?> body = call.getArgument(1);
            return json.valueToTree(
                Map.of(
                    "modelVersion",
                    version,
                    "vectors",
                    ((List<?>) body.get("texts")).isEmpty()
                        ? List.of()
                        : List.of(Collections.nCopies(1024, 0.1))
                )
            );
        });
        when(
            runtime.writeMemoryVector(
                anyString(),
                eq(10L),
                eq("fixture"),
                eq(version)
            )
        ).thenReturn(1);
        background().maintain();
        verify(runtime).missingMemoryVectors(version);
        verify(runtime).writeMemoryVector(
            anyString(),
            eq(10L),
            eq("fixture"),
            eq(version)
        );
        verify(redis).delete("meng:companion:memo:42");
    }

    @Test
    void unavailableVersionDoesNotOverwriteVectorsOrPreventLocalCleanup() {
        when(ai.compute(eq("embeddings"), anyMap())).thenThrow(
            new IllegalStateException("offline")
        );
        background().maintain();
        verify(runtime, never()).missingMemoryVectors(anyString());
        verify(runtime, never()).writeMemoryVector(
            anyString(),
            anyLong(),
            anyString(),
            anyString()
        );
        verify(runtime).purgeExpiredMessages();
    }
}

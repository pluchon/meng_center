package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.service.CompanionMemoryCache;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class CompanionMemoryCacheTest {

    private final CompanionMapper mapper = mock(CompanionMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(
        ValueOperations.class
    );

    private final AtomicReference<String> stored = new AtomicReference<>();
    private final Rows.Bond bond = new Rows.Bond();
    private final CompanionMemoryCache cache = new CompanionMemoryCache(
        mapper,
        redis
    );

    @BeforeEach
    void setup() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("meng:companion:memo:42")).thenAnswer(call ->
            stored.get()
        );
        doAnswer(call -> {
            stored.set(call.getArgument(1));
            return null;
        })
            .when(values)
            .set(
                eq("meng:companion:memo:42"),
                anyString(),
                eq(Duration.ofHours(1))
            );
        when(mapper.bond(42, "NEKO")).thenReturn(bond);
        var memory = new Rows.Memory();
        memory.setId(1L);
        memory.setContent("喜欢蓝色");
        memory.setEmbedding("[1,0]");
        when(mapper.memories(42, "NEKO")).thenReturn(List.of(memory));
    }

    @Test
    void repeatedReadReusesMemoryAndVectorForOneHour() {
        assertEquals("[1,0]", cache.get(42, "NEKO").get(0).getEmbedding());
        assertEquals("喜欢蓝色", cache.get(42, "NEKO").get(0).getContent());
        verify(mapper, times(1)).memories(42, "NEKO");
        verify(values).set(
            eq("meng:companion:memo:42"),
            anyString(),
            eq(Duration.ofHours(1))
        );
    }

    @Test
    void clearRejectsAnOldCacheEvenIfItWasWrittenAfterInvalidation() {
        cache.get(42, "NEKO");
        bond.setRevision(1);
        when(mapper.memories(42, "NEKO")).thenReturn(List.of());
        assertTrue(cache.get(42, "NEKO").isEmpty());
    }

    @Test
    void factUpdateRejectsPreviousProfileRevision() {
        cache.get(42, "NEKO");
        bond.setProfileRevision(1);
        when(mapper.memories(42, "NEKO")).thenReturn(List.of());
        assertTrue(cache.get(42, "NEKO").isEmpty());
    }

    @Test
    void unavailableOrCorruptedCacheFallsBackToDatabase() {
        stored.set("invalid JSON");
        assertEquals(1, cache.get(42, "NEKO").size());
        when(values.get(anyString())).thenThrow(
            new IllegalStateException("offline")
        );
        doThrow(new IllegalStateException("offline"))
            .when(values)
            .set(anyString(), anyString(), any(Duration.class));
        assertEquals(1, cache.get(42, "NEKO").size());
    }
}

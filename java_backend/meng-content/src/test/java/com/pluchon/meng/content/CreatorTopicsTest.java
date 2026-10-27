package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pluchon.meng.content.service.ContentAssistantService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class CreatorTopicsTest {
    @Test
    void summaryUsesOnlyTheAuthorsTopicsAndPositiveTopicWeights() {
        var service = new ContentAssistantService();
        var jdbc = mock(JdbcTemplate.class);
        var redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(service,"jdbc",jdbc);
        ReflectionTestUtils.setField(service,"redis",redis);
        when(jdbc.queryForList(anyString(),eq(42L),any(Timestamp.class),any(Timestamp.class)))
            .thenReturn(List.of(Map.of("topics","[\"Vue\",\"Vue\",\"游戏\"]"),
                                Map.of("topics","[\"游戏\",\"日常\"]")));
        when(redis.opsForHash().entries("content:rec:profile:42"))
            .thenReturn(Map.of("t:游戏","9","t:摄影","5","c:TECH","100","_count","99","t:过期","0"));
        var start = Timestamp.from(Instant.parse("2026-10-01T00:00:00Z"));
        var end = Timestamp.from(Instant.parse("2026-10-27T00:00:00Z"));
        assertEquals(List.of("游戏","Vue","日常"),
            ReflectionTestUtils.invokeMethod(service,"writtenTopics",42L,start,end));
        assertEquals(List.of("游戏","摄影"),ReflectionTestUtils.invokeMethod(service,"interestTopics",42L));
    }
}

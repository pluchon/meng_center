package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;

import com.pluchon.meng.content.config.SearchProperties;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.service.HotRankService;
import com.pluchon.meng.content.service.PostSearchIndex;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.Duration;

class ReadModelFailureTest {

    @Test
    void rebuildInfrastructureFailuresAreReportedToScheduler() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        assertFalse(hot(redis, null).rebuild());
        assertFalse(index(null, redis).rebuild());
    }

    @Test
    void failedElasticRequestOpensCooldownInsteadOfRepeatedlyBlockingReads() throws Exception {
        var client = mock(ElasticsearchClient.class);
        when(client.search(any(SearchRequest.class), eq(Void.class)))
                .thenThrow(new IOException("offline"));
        var service = index(client, null);
        assertNull(service.search("萌", null, PostSort.HOT, 1, 5));
        assertNull(service.search("萌", null, PostSort.LATEST, 1, 5));
        verify(client, times(1)).search(any(SearchRequest.class), eq(Void.class));
    }

    @Test
    void redisFailureFallsBackWithoutThrowingIntoFeed() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("offline"));
        var service = hot(redis, null);
        assertNull(service.read(null, 1, 5));
    }

    @Test
    void missingRankCoalescesRepeatedRebuildRequests() {
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var executor = mock(ThreadPoolTaskExecutor.class);
        var service = hot(redis, executor);
        assertNull(service.read(null, 1, 5));
        assertNull(service.read(null, 1, 5));
        verify(executor, times(1)).execute(any(Runnable.class));
    }

    private HotRankService hot(StringRedisTemplate redis, ThreadPoolTaskExecutor executor) {
        var service = new HotRankService();
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "executor", executor);
        return service;
    }

    private PostSearchIndex index(ElasticsearchClient client, StringRedisTemplate redis) {
        var service = new PostSearchIndex();
        ReflectionTestUtils.setField(service, "client", client);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(
                service,
                "properties",
                new SearchProperties("ik_max_word", "ik_smart", Duration.ofSeconds(10)));
        return service;
    }
}

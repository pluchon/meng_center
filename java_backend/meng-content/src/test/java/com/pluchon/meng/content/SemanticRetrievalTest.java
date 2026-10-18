package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.client.AiClient;
import com.pluchon.meng.content.client.AiComputation;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.service.ContentLimitService;
import com.pluchon.meng.content.service.PostSearchService;
import com.pluchon.meng.content.service.PostService;
import com.pluchon.meng.content.service.PostVectorIndex;
import com.pluchon.meng.content.service.SemanticSearchService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class SemanticRetrievalTest {

    private final AiClient ai = mock(AiClient.class);
    private final PostVectorIndex vectors = mock(PostVectorIndex.class);
    private final PostMapper posts = mock(PostMapper.class);
    private final PostService feed = mock(PostService.class);
    private final PostSearchService keyword = mock(PostSearchService.class);
    private final SemanticSearchService service = new SemanticSearchService();
    private PostEntity first;
    private PostEntity second;

    @BeforeEach
    void setup() throws Exception {
        UserContext.bind(new RequestIdentity(42, "USER", "fixture"));
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(null);
        when(ai.embeddings(any())).thenReturn(
            Map.of("vectors", List.of(Collections.nCopies(1024, 0.1f)))
        );
        when(vectors.nearest(anyList(), isNull(), eq(0), eq(20))).thenReturn(
            List.of(hit(1), hit(2), hit(3))
        );
        first = post(1, "quiet 是变量名");
        second = post(2, "在院子里喝热可可看落叶");
        var removed = post(3, "不可公开的候选");
        removed.setDeleteState(1);
        when(posts.selectByIds(anyCollection())).thenReturn(
            List.of(first, second, removed)
        );
        when(feed.hydrate(anyList())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "ai", ai);
        ReflectionTestUtils.setField(service, "vectors", vectors);
        ReflectionTestUtils.setField(service, "posts", posts);
        ReflectionTestUtils.setField(service, "feed", feed);
        ReflectionTestUtils.setField(service, "keyword", keyword);
        when(
            keyword.search(anyString(), isNull(), eq(PostSort.RELEVANT), eq(1L), eq(10L), eq(false))
        ).thenReturn(new PageResult<>(List.of(), 7, 1, 10));
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(
            service,
            "limits",
            mock(ContentLimitService.class)
        );
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void agentChoosesByMeaningAfterVisibilityChecks() {
        when(ai.retrieve(any())).thenReturn(
            new AiComputation.RetrievalResponse(List.of(1), 30)
        );
        var result = service.search("A quiet moment outside", null, 1, 10);
        assertEquals(1, result.total());
        verify(feed).hydrate(List.of(second));
        var request = ArgumentCaptor.forClass(
            AiComputation.RetrievalRequest.class
        );
        verify(ai).retrieve(request.capture());
        assertEquals(42, request.getValue().userId());
        assertEquals(2, request.getValue().candidates().size());
    }

    @Test
    void agentCanRejectAllCandidatesWithoutFillingResults() {
        when(ai.retrieve(any())).thenReturn(
            new AiComputation.RetrievalResponse(List.of(), 30)
        );
        assertEquals(0, service.search("水星地形", null, 1, 10).total());
        verify(feed).hydrate(List.of());
    }

    @Test
    void invalidAgentDecisionsFallBackToKeywordSearch() {
        for (var ranked : List.of(List.of(2), List.of(0, 0), List.of(-1))) {
            when(ai.retrieve(any())).thenReturn(
                new AiComputation.RetrievalResponse(ranked, 30)
            );
            var result = service.search("查询", null, 1, 10);
            assertFalse(result.semantic());
            assertEquals(7, result.total());
        }
        when(ai.retrieve(any())).thenThrow(
            new IllegalStateException("agent offline")
        );
        assertFalse(service.search("查询", null, 1, 10).semantic());
        verify(feed, never()).hydrate(anyList());
    }

    @Test
    void vectorFailureFallsBackToKeywordSearch() throws Exception {
        when(vectors.nearest(anyList(), isNull(), eq(0), eq(20))).thenThrow(
            new IOException("offline")
        );
        var result = service.search("查询", null, 1, 10);
        assertFalse(result.semantic());
        assertEquals(7, result.total());
        verify(ai, never()).retrieve(any());
    }

    private static PostEntity post(long id, String title) {
        var post = new PostEntity();
        post.setId(id);
        post.setTitle(title);
        post.setSummary(title);
        post.setDeleteState(0);
        post.setCreateTime(LocalDateTime.of(2026, 10, 18, 0, 0));
        return post;
    }

    private static PostVectorIndex.Hit hit(long id) {
        return new PostVectorIndex.Hit(
            new PostVectorIndex.Document(
                id,
                List.of(0.1f),
                "DAILY",
                List.of(),
                10,
                3,
                "PASS",
                "2026-10-18T00:00:00Z",
                "fixture"
            ),
            0.9
        );
    }
}

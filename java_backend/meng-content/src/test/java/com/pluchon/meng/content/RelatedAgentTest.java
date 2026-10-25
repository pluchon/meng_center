package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.client.AiClient;
import com.pluchon.meng.content.client.AiComputation;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.service.PostService;
import com.pluchon.meng.content.service.PostVectorIndex;
import com.pluchon.meng.content.service.RelatedPostService;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RelatedAgentTest {

    private final RelatedPostService service = new RelatedPostService();
    private final AiClient ai = mock(AiClient.class);
    private final PostVectorIndex vectors = mock(PostVectorIndex.class);
    private final PostMapper posts = mock(PostMapper.class);
    private final PostService feed = mock(PostService.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private PostEntity second;

    @BeforeEach
    void setup() throws Exception {
        UserContext.bind(new RequestIdentity(42, "USER", "fixture"));
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("content:related:99")).thenReturn("[1,2]");
        when(posts.selectById(99L)).thenReturn(post(99, "源帖"));
        when(vectors.get(99)).thenReturn(document(99));
        when(vectors.nearest(anyList(), isNull(), eq(0), eq(21))).thenReturn(
            List.of(
                new PostVectorIndex.Hit(document(1), 0.9),
                new PostVectorIndex.Hit(document(2), 0.4)
            )
        );
        second = post(2, "含义相关的另一种表达");
        when(posts.selectByIds(anyCollection())).thenReturn(
            List.of(post(1, "字面重合但无关"), second)
        );
        when(feed.hydrate(anyList())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "ai", ai);
        ReflectionTestUtils.setField(service, "vectors", vectors);
        ReflectionTestUtils.setField(service, "posts", posts);
        ReflectionTestUtils.setField(service, "feed", feed);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void legacyTagCacheIsIgnoredAndAgentChoosesTheFinalRelatedPost() {
        when(ai.retrieve(any())).thenReturn(
            new AiComputation.RetrievalResponse(List.of(1), List.of(0.9), true)
        );
        assertEquals(1, service.related(99, 1, 5).total());
        verify(feed).hydrate(List.of(second));
        verifyNoInteractions(jdbc);
    }

    @Test
    void unavailableAgentFallsBackToVectorOrder() {
        when(ai.retrieve(any())).thenThrow(
            new IllegalStateException("agent offline")
        );
        assertEquals(2, service.related(99, 1, 5).total());
        verifyNoInteractions(jdbc);
    }

    @Test
    void unavailableVectorsLeaveRelatedPostsEmpty() throws Exception {
        when(vectors.get(99)).thenThrow(new IOException("offline"));
        assertEquals(0, service.related(99, 1, 5).total());
        verifyNoInteractions(jdbc, ai, feed);
    }

    private static PostEntity post(long id, String title) {
        var post = new PostEntity();
        post.setId(id);
        post.setAuthorId(10L);
        post.setTitle(title);
        post.setSummary(title);
        post.setDeleteState(0);
        return post;
    }

    private static PostVectorIndex.Document document(long id) {
        return new PostVectorIndex.Document(
            id,
            List.of(0.1f),
            "DAILY",
            List.of(),
            10,
            3,
            "PASS",
            "2026-10-18T00:00:00Z",
            "fixture"
        );
    }
}

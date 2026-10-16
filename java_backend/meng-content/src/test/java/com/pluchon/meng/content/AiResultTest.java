package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pluchon.meng.content.controller.dto.PostAnalysisDTO;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.service.AiResultService;
import com.pluchon.meng.content.service.PostVectorIndex;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class AiResultTest {

    private final AiResultService service = new AiResultService();
    private final PostMapper posts = mock(PostMapper.class);
    private final PostVectorIndex vectors = mock(PostVectorIndex.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final LocalDateTime version = LocalDateTime.parse("2026-10-16T03:00:00.123456");

    private PostAnalysisDTO result(String verdict) {
        return new PostAnalysisDTO(
            3,
            version,
            "model:v1",
            List.of("Vue"),
            List.of(),
            3,
            verdict,
            List.of(),
            "reason",
            java.util.Collections.nCopies(1024, .1f),
            "SUCCESS",
            true,
            List.of()
        );
    }

    private PostEntity post() {
        var post = new PostEntity();
        post.setId(3L);
        post.setAuthorId(9L);
        post.setCategory(PostCategory.TECH);
        post.setUpdateTime(version);
        post.setCreateTime(version);
        return post;
    }

    private void setup() {
        ReflectionTestUtils.setField(service, "posts", posts);
        ReflectionTestUtils.setField(service, "vectors", vectors);
        ReflectionTestUtils.setField(service, "jdbc", jdbc);
        ReflectionTestUtils.setField(
            service,
            "events",
            mock(org.springframework.context.ApplicationEventPublisher.class)
        );
        ReflectionTestUtils.setField(
            service,
            "validator",
            Validation.buildDefaultValidatorFactory().getValidator()
        );
    }

    @Test
    void staleAnalysisDoesNotTouchFeaturesOrIndex() throws Exception {
        setup();
        var post = post();
        post.setUpdateTime(version.plusSeconds(1));
        when(posts.selectByIdForUpdate(3)).thenReturn(post);
        service.post("event-stale", result("PASS"));
        verifyNoInteractions(jdbc, vectors);
    }

    @Test
    void invalidVectorIsRejectedBeforePersistence() {
        setup();
        var result = result("PASS");
        var invalid = new PostAnalysisDTO(
            3,
            version,
            "v",
            result.topics(),
            List.of(),
            3,
            "PASS",
            List.of(),
            "",
            List.of(Float.NaN),
            "SUCCESS",
            true,
            List.of()
        );
        assertThrows(IllegalArgumentException.class, () -> service.post("event-bad", invalid));
        verifyNoInteractions(posts, jdbc, vectors);
    }

    @Test
    void disabledEnforcementPreservesPublicStateForBlock() throws Exception {
        setup();
        var post = post();
        when(posts.selectByIdForUpdate(3)).thenReturn(post);
        when(jdbc.queryForList(anyString(), eq(3L))).thenReturn(List.of());
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        service.post("event-block", result("BLOCK"));
        assertEquals("NORMAL", post.getReviewState().name());
        verify(vectors).write(
            eq(post),
            anyList(),
            eq(List.of("Vue")),
            eq(3),
            eq("BLOCK"),
            eq("model:v1")
        );
    }

    @Test
    void textOnlyEditRetainsPriorImageBlock() throws Exception {
        setup();
        var post = post();
        when(posts.selectByIdForUpdate(3)).thenReturn(post);
        when(jdbc.queryForList(anyString(), eq(3L))).thenReturn(
            List.of(
                java.util.Map.of(
                    "post_version",
                    version.minusSeconds(1),
                    "manual_review",
                    0,
                    "image_reviews",
                    "[{\"imageId\":8,\"verdict\":\"BLOCK\",\"labels\":[\"暴力\"],\"reason\":\"违规配图\"}]"
                )
            )
        );
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        var initial = result("PASS");
        var edited = new PostAnalysisDTO(
            3,
            version,
            initial.modelVersion(),
            initial.topics(),
            List.of(),
            3,
            "PASS",
            List.of(),
            "",
            initial.embedding(),
            "SUCCESS",
            false,
            List.of()
        );
        service.post("text-edit", edited);
        verify(vectors).write(eq(post), anyList(), anyList(), eq(3), eq("BLOCK"), anyString());
    }

    @Test
    void manualApprovalOfSameVersionOverridesRepeatedAnalysis() throws Exception {
        setup();
        var post = post();
        when(posts.selectByIdForUpdate(3)).thenReturn(post);
        when(jdbc.queryForList(anyString(), eq(3L))).thenReturn(
            List.of(
                java.util.Map.of("post_version", version, "manual_review", 1, "image_reviews", "[]")
            )
        );
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        ReflectionTestUtils.setField(service, "enforce", true);
        service.post("manual-reviewed", result("BLOCK"));
        verify(vectors).write(eq(post), anyList(), anyList(), eq(3), eq("PASS"), anyString());
        assertEquals("NORMAL", post.getReviewState().name());
    }
}

package com.pluchon.meng.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.content.controller.dto.CreateCommentDTO;
import com.pluchon.meng.content.controller.dto.CreatePostDTO;
import com.pluchon.meng.content.controller.dto.CreateReportDTO;
import com.pluchon.meng.content.controller.dto.SaveDraftDTO;
import com.pluchon.meng.content.controller.vo.CommentVO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.controller.vo.MentionVO;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.converter.CommentConverter;
import com.pluchon.meng.content.converter.PostConverter;
import com.pluchon.meng.content.converter.ReportConverter;
import com.pluchon.meng.content.entity.CommentEntity;
import com.pluchon.meng.content.entity.CommentImageEntity;
import com.pluchon.meng.content.entity.CommentMentionEntity;
import com.pluchon.meng.content.entity.PostDraftEntity;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.ReportReason;
import com.pluchon.meng.content.entity.enums.ReportTarget;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

class ContentConverterTest {

    @Test
    void recordPostCopiesEverySharedFieldAndKeepsCounterDefaults() {
        var dto =
                new CreatePostDTO(
                        PostCategory.TECH, " 标题 ", "正文", List.of(9L), 9L, List.of("标签"), 7L);
        var post = new PostConverter().create(dto);
        assertEquals(dto.category(), post.getCategory());
        assertEquals("标题", post.getTitle());
        assertEquals(dto.content(), post.getContent());
        assertEquals(dto.coverImageId(), post.getCoverImageId());
        assertEquals(0L, post.getViewCount());
        assertEquals(0L, post.getLikeCount());
        assertEquals(0L, post.getCommentCount());
        assertEquals(0L, post.getHotScore());
        assertEquals(0, post.getImageCount());
        assertNull(post.getAuthorId());
    }

    @Test
    void draftCopiesAllFieldsAndClearsNullableCoverWithoutChangingIdentity() {
        var draft = new PostDraftEntity();
        draft.setId(11L);
        draft.setAuthorId(22L);
        draft.setCoverImageId(33L);
        var dto =
                new SaveDraftDTO(
                        PostCategory.GAME, " 草稿标题 ", "草稿正文", List.of(44L), null, List.of("标签"));
        new PostConverter().applyDraft(dto, draft);
        assertEquals(dto.category(), draft.getCategory());
        assertEquals(dto.title(), draft.getTitle());
        assertEquals(dto.content(), draft.getContent());
        assertEquals(dto.imageIds(), draft.getImageIds());
        assertEquals(dto.coverImageId(), draft.getCoverImageId());
        assertEquals(dto.tags(), draft.getTags());
        assertEquals(11L, draft.getId());
        assertEquals(22L, draft.getAuthorId());
    }

    @Test
    void commentCopiesEverySharedFieldAndKeepsCountersZero() {
        var dto = new CreateCommentDTO("评论内容", List.of(8L), 6L, List.of());
        var comment = new CommentConverter().create(dto);
        assertEquals(dto.content(), comment.getContent());
        assertEquals(dto.replyToCommentId(), comment.getReplyToCommentId());
        assertEquals(0L, comment.getLikeCount());
        assertEquals(0L, comment.getReplyCount());
        assertEquals(0, comment.getImageCount());
        assertNull(comment.getAuthorId());
    }

    @Test
    void reportCopiesTargetReasonAndNoteIncludingPrimitiveId() {
        var dto = new CreateReportDTO(ReportTarget.POST, 15L, ReportReason.SPOILER, "说明");
        var report = new ReportConverter().create(dto);
        assertEquals(dto.targetType(), report.getTargetType());
        assertEquals(dto.targetId(), report.getTargetId());
        assertEquals(dto.reason(), report.getReason());
        assertEquals(dto.note(), report.getNote());
        assertNull(report.getReporterId());
        assertNull(report.getStatus());
    }

    @Test
    void postImageAndFeedCopyEveryOutputField() {
        var image = new PostImageEntity();
        image.setId(8L);
        image.setFileName("original.png");
        image.setThumbName("thumb.png");
        image.setWidth(800);
        image.setHeight(600);
        var expectedImage =
                new ImageVO(
                        8L, "/content/files/original.png", "/content/files/thumb.png", 800, 600);
        var converter = new PostConverter();
        assertEquals(expectedImage, converter.image(image));
        var post = new PostEntity();
        post.setId(2L);
        post.setAuthorId(3L);
        post.setCategory(PostCategory.DAILY);
        post.setTitle("标题");
        post.setSummary("摘要");
        post.setImageCount(1);
        post.setViewCount(4L);
        post.setLikeCount(5L);
        post.setCommentCount(6L);
        post.setIpRegion("台湾");
        var time = LocalDateTime.of(2026, 10, 11, 12, 0);
        post.setCreateTime(time);
        var author = new PublicUserVO(3L, "作者", "avatar.png", 2);
        assertEquals(
                new PostFeedVO(
                        2L,
                        PostCategory.DAILY,
                        "标题",
                        "摘要",
                        List.of(expectedImage),
                        1,
                        4,
                        5,
                        6,
                        7,
                        true,
                        true,
                        "台湾",
                        time,
                        author),
                converter.feed(post, List.of(image), author, true, true, 7));
        assertEquals(
                new PublicUserVO(3L, null, null, 1),
                converter.feed(post, List.of(), null, false, false, 0).author());
    }

    @Test
    void commentImageAndViewCopyEveryOutputFieldWithAuthorAndReplyTarget() {
        var image = new CommentImageEntity();
        image.setId(8L);
        image.setFileName("original.png");
        image.setThumbName("thumb.png");
        image.setWidth(800);
        image.setHeight(600);
        var expectedImage =
                new ImageVO(
                        8L, "/content/files/original.png", "/content/files/thumb.png", 800, 600);
        var converter = new CommentConverter();
        assertEquals(expectedImage, converter.image(image));
        var comment = new CommentEntity();
        comment.setId(2L);
        comment.setAuthorId(3L);
        comment.setContent("评论");
        comment.setLikeCount(4L);
        comment.setReplyCount(5L);
        comment.setReplyToUserId(6L);
        comment.setIpRegion("台湾");
        comment.setPinnedTime(LocalDateTime.of(2026, 10, 12, 8, 0));
        var mention = new CommentMentionEntity();
        mention.setUserId(6L);
        mention.setNickname("回复对象");
        var time = LocalDateTime.of(2026, 10, 11, 12, 0);
        comment.setCreateTime(time);
        var author = new PublicUserVO(3L, "作者", "avatar.png", 2);
        var target = new PublicUserVO(6L, "回复对象", null, 1);
        assertEquals(
                new CommentVO(
                        2L,
                        "评论",
                        List.of(expectedImage),
                        4,
                        true,
                        "台湾",
                        time,
                        author,
                        true,
                        5,
                        6L,
                        "回复对象",
                        true,
                        List.of(new MentionVO(6L, "回复对象"))),
                converter.comment(
                        comment,
                        List.of(image),
                        List.of(mention),
                        Map.of(3L, author, 6L, target),
                        true,
                        3L));
        var fallback = converter.comment(comment, List.of(), List.of(), Map.of(), false, 9L);
        assertEquals(new PublicUserVO(3L, null, null, 1), fallback.author());
        assertNull(fallback.replyToNickname());
    }
}

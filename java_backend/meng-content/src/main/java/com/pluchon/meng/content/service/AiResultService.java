package com.pluchon.meng.content.service;

import com.pluchon.meng.content.controller.dto.PostAnalysisDTO;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.PostMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

// AI 结果的版本校验、幂等保存与可见状态处理
@Service
public class AiResultService {

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 帖子数据与内容版本
    @Autowired
    private PostMapper posts;

    // 帖子向量读写与召回
    @Autowired
    private PostVectorIndex vectors;

    // 事务内领域事件发布
    @Autowired
    private ApplicationEventPublisher events;

    // 分析结果契约校验
    @Autowired
    private jakarta.validation.Validator validator;

    // 本域事务发件箱
    @Autowired
    private AiEventService outbox;

    // 帖子审核结论落地的开关：关闭时只记录结论；开启时判违规的帖子退回草稿箱
    @Value("${meng.ai.moderation.enforce:false}")
    private boolean enforce;

    // 退回草稿箱与帖子配图、标签的联动
    @Autowired
    private PostService postService;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    // 按内容版本和事件标识幂等保存分析结论
    @Transactional(rollbackFor = Exception.class)
    public void post(String eventId, PostAnalysisDTO result) throws java.io.IOException {
        if (
            !validator.validate(result).isEmpty() ||
            result
                .embedding()
                .stream()
                .anyMatch(v -> v == null || !Float.isFinite(v))
        ) throw new IllegalArgumentException("Invalid analysis result");
        var post = posts.selectByIdForUpdate(result.postId());
        if (post == null) {
            vectors.delete(result.postId());
            events.publishEvent(new Indexed(result.postId()));
            return;
        }
        if (!result.version().equals(post.getUpdateTime())) return;
        var prior = jdbc.queryForList(
            "SELECT post_version,manual_review,image_reviews FROM post_ai_feature WHERE post_id=?",
            post.getId()
        );
        java.time.LocalDateTime priorVersion = prior.isEmpty()
            ? null
            : asTime(prior.get(0).get("post_version"));
        if (priorVersion != null && priorVersion.isAfter(result.version())) return;
        boolean manuallyApproved =
            priorVersion != null &&
            priorVersion.equals(result.version()) &&
            ((Number) prior.get(0).get("manual_review")).intValue() == 1;
        var reviews = result.imageReviews();
        if (!result.imagesChanged() && !prior.isEmpty()) reviews = json.readValue(
            prior.get(0).get("image_reviews").toString(),
            new tools.jackson.core.type.TypeReference<List<PostAnalysisDTO.ImageReview>>() {}
        );
        String verdict = result.verdict(),
            reason = result.reason();
        var labels = new java.util.LinkedHashSet<>(result.labels());
        var severity = Map.of("PASS", 0, "SUSPECT", 1, "BLOCK", 2);
        for (var review : reviews) {
            if (severity.get(review.verdict()) > severity.get(verdict)) {
                verdict = review.verdict();
                reason = review.reason();
            }
            for (var label : review.labels()) labels.add("image:" + review.imageId() + ":" + label);
        }
        if (
            jdbc.update("INSERT IGNORE INTO ai_result_inbox(event_id) VALUES (?)", eventId) == 0
        ) return;
        jdbc.update(
            "INSERT INTO post_ai_feature(post_id,post_version,model_version,topics,keywords,labels,quality,verdict,reason,analyze_state,image_reviews) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE post_version=VALUES(post_version),model_version=VALUES(model_version),topics=VALUES(topics),keywords=VALUES(keywords),labels=VALUES(labels),quality=VALUES(quality),verdict=VALUES(verdict),reason=VALUES(reason),analyze_state=VALUES(analyze_state),image_reviews=VALUES(image_reviews),delete_state=0",
            result.postId(),
            result.version(),
            result.modelVersion(),
            json.writeValueAsString(result.topics()),
            json.writeValueAsString(result.keywords()),
            json.writeValueAsString(labels),
            result.quality(),
            verdict,
            reason,
            result.analyzeState(),
            json.writeValueAsString(reviews)
        );
        String state = post.getReviewState().name();
        String effectiveVerdict = manuallyApproved ? "PASS" : verdict;
        jdbc.update(
            "UPDATE post_ai_feature SET manual_review=? WHERE post_id=?",
            manuallyApproved ? 1 : 0,
            post.getId()
        );
        if (enforce && "BLOCK".equals(effectiveVerdict)) {
            long draftId = postService.withdrawToDraft(post);
            notify(
                "post.review-changed",
                Map.<String, Object>of(
                    "postId",
                    post.getId(),
                    "postTitle",
                    post.getTitle(),
                    "authorId",
                    post.getAuthorId(),
                    "reviewState",
                    "WITHDRAWN",
                    "reason",
                    shortReason(reason),
                    "draftId",
                    draftId,
                    "labels",
                    labels
                )
            );
            events.publishEvent(new Indexed(post.getId()));
            return;
        }
        // 早先版本把违规帖子标为「审核中」，仍在审核中的帖子这次通过就恢复展示
        if ("PASS".equals(effectiveVerdict) && "IN_REVIEW".equals(state)) state = "NORMAL";
        if (!state.equals(post.getReviewState().name())) {
            jdbc.update(
                "UPDATE post SET review_state=?,update_time=update_time WHERE id=?",
                state,
                post.getId()
            );
            var body = Map.<String, Object>of(
                "postId",
                post.getId(),
                "postTitle",
                post.getTitle(),
                "authorId",
                post.getAuthorId(),
                "reviewState",
                state,
                "labels",
                labels
            );
            notify("post.review-changed", body);
            post.setReviewState(com.pluchon.meng.content.entity.enums.ReviewState.valueOf(state));
            events.publishEvent(
                new PostReadModelEvent(
                    post.getId(),
                    post.getCategory(),
                    PostReadModelEvent.Kind.UPDATED,
                    0
                )
            );
        }
        vectors.write(
            post,
            result.embedding(),
            result.topics(),
            result.quality(),
            effectiveVerdict,
            result.modelVersion()
        );
        events.publishEvent(new Indexed(post.getId()));
    }

    // 保存评论审核结果并尊重人工复核
    @Transactional(rollbackFor = Exception.class)
    public void comment(String eventId, Map<String, Object> result) {
        long id = ((Number) result.get("commentId")).longValue();
        String verdict = (String) result.get("verdict");
        if (
            !List.of("PASS", "SUSPECT", "BLOCK").contains(verdict)
        ) throw new IllegalArgumentException("Invalid verdict");
        var rows = jdbc.queryForList(
            "SELECT c.*,p.title FROM comment c JOIN post p ON p.id=c.post_id WHERE c.id=? AND c.delete_state=0 AND p.delete_state=0 FOR UPDATE",
            id
        );
        if (
            rows.isEmpty() ||
            jdbc.update("INSERT IGNORE INTO ai_result_inbox(event_id) VALUES (?)", eventId) == 0
        ) return;
        jdbc.update(
            "INSERT INTO comment_ai_review(comment_id,verdict,labels,reason,model_version) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE verdict=VALUES(verdict),labels=VALUES(labels),reason=VALUES(reason),model_version=VALUES(model_version)",
            id,
            verdict,
            json.writeValueAsString(result.get("labels")),
            result.get("reason"),
            result.get("modelVersion")
        );
        var row = rows.get(0);
        boolean manuallyApproved =
            jdbc.queryForObject(
                "SELECT manual_review FROM comment_ai_review WHERE comment_id=?",
                Integer.class,
                id
            ) == 1;
        if (
            enforce &&
            !manuallyApproved &&
            "BLOCK".equals(verdict) &&
            !"IN_REVIEW".equals(row.get("review_state"))
        ) {
            jdbc.update(
                "UPDATE comment SET review_state='IN_REVIEW',update_time=update_time WHERE id=?",
                id
            );
            notify(
                "comment.review-changed",
                Map.of(
                    "commentId",
                    id,
                    "postId",
                    row.get("post_id"),
                    "postTitle",
                    row.get("title"),
                    "authorId",
                    row.get("author_id"),
                    "excerpt",
                    row
                        .get("content")
                        .toString()
                        .substring(0, Math.min(100, row.get("content").toString().length())),
                    "reviewState",
                    "IN_REVIEW",
                    "labels",
                    result.get("labels")
                )
            );
        }
    }

    // 通知里的理由只留一句话，最多 80 个字
    private String shortReason(String reason) {
        if (reason == null) return "";
        String text = reason.strip().replaceAll("\s+", " ");
        return text.codePointCount(0, text.length()) <= 80
            ? text
            : text.substring(0, text.offsetByCodePoints(0, 80)) + "…";
    }

    // 将审核通知写入同域事务发件箱
    private void notify(String type, Map<String, Object> body) {
        outbox.enqueue(type, body);
        events.publishEvent(new ReviewNotification());
    }

    // 统一数据库微秒时间戳类型
    private java.time.LocalDateTime asTime(Object value) {
        return value instanceof java.time.LocalDateTime time
            ? time
            : ((java.sql.Timestamp) value).toLocalDateTime();
    }

    public record Indexed(long post) {}

    // 事务提交后完成当前重建条目
    @org.springframework.transaction.event.TransactionalEventListener
    public void indexed(Indexed event) throws java.io.IOException {
        vectors.completed(event.post());
    }

    public record ReviewNotification() {}

    // 审核事务提交后异步补发通知
    @org.springframework.transaction.event.TransactionalEventListener
    public void notifyCommitted(ReviewNotification event) {
        outbox.dispatchAsync();
    }
}

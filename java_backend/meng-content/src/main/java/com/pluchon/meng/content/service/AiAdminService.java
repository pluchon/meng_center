package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 内容复核与向量索引重建
@org.springframework.stereotype.Service
public class AiAdminService {

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 帖子批次和版本查询
    @Autowired
    private com.pluchon.meng.content.mapper.PostMapper postMapper;

    // 向量索引与重建进度
    @Autowired
    private PostVectorIndex vectorIndex;

    // 事务内领域事件发布
    @Autowired
    private org.springframework.context.ApplicationEventPublisher events;

    // 串行执行后台维护任务
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("contentMaintenanceExecutor")
    private org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor maintenance;

    // 合并当前进程的重复重建请求
    private final java.util.concurrent.atomic.AtomicBoolean analyzing =
        new java.util.concurrent.atomic.AtomicBoolean();

    // 提交或恢复向量重建，合并重复请求
    public void reanalyze(boolean recompute) {
        admin();
        if (!analyzing.compareAndSet(false, true)) return;
        try {
            maintenance.execute(() -> {
                try {
                    if (!recompute) {
                        vectorIndex.reindex();
                        return;
                    }
                    if (!vectorIndex.beginRebuild() && !vectorIndex.issuing()) return;
                    long cursor = 0;
                    while (true) {
                        var batch = postMapper.selectRebuildBatch(cursor, null);
                        if (batch.isEmpty()) break;
                        vectorIndex.expect(
                            batch
                                .stream()
                                .map(com.pluchon.meng.content.entity.PostEntity::getId)
                                .toList()
                        );
                        for (var post : batch)
                            outbox.post(post.getId(), "post.analysis-requested", true);
                        cursor = batch.get(batch.size() - 1).getId();
                        outbox.dispatch();
                    }
                    vectorIndex.finishIssuing();
                } catch (java.io.IOException error) {
                    org.slf4j.LoggerFactory.getLogger(AiAdminService.class).warn(
                        "Vector rebuild deferred"
                    );
                } finally {
                    analyzing.set(false);
                }
            });
        } catch (RuntimeException error) {
            analyzing.set(false);
            throw error;
        }
    }

    // 帖子数据与内容版本
    @Autowired
    private PostService posts;

    // 评论数据与删除规则
    @Autowired
    private CommentService comments;

    // 本域事务发件箱
    @Autowired
    private AiEventService outbox;

    // 校验当前管理员身份
    private void admin() {
        if (!"ADMIN".equals(UserContext.require().role())) throw new BusinessException(
            CommonErrorCode.FORBIDDEN
        );
    }

    // 列出需要人工查看的内容结论
    public List<Map<String, Object>> list() {
        admin();
        return jdbc.queryForList(
            "SELECT 'POST' kind,post_id id,verdict,labels,reason,model_version FROM post_ai_feature WHERE verdict<>'PASS' AND delete_state=0 UNION ALL SELECT 'COMMENT',comment_id,verdict,labels,reason,model_version FROM comment_ai_review WHERE verdict<>'PASS' AND delete_state=0 LIMIT 100"
        );
    }

    // 人工通过恢复展示，拒绝沿用域内删除规则
    @Transactional(rollbackFor = Exception.class)
    public void decide(String kind, long id, boolean approved) {
        admin();
        if ("POST".equals(kind)) {
            if (approved) {
                jdbc.update(
                    "UPDATE post SET review_state='NORMAL',update_time=update_time WHERE id=? AND delete_state=0",
                    id
                );
                jdbc.update("UPDATE post_ai_feature SET manual_review=1 WHERE post_id=?", id);
                outbox.post(id, "post.analysis-requested", true);
                var post = postMapper.selectById(id);
                if (post != null) {
                    events.publishEvent(
                        new com.pluchon.meng.content.entity.event.PostReadModelEvent(
                            id,
                            post.getCategory(),
                            com.pluchon.meng.content.entity.event.PostReadModelEvent.Kind.UPDATED,
                            0
                        )
                    );
                    outbox.enqueue(
                        "post.review-changed",
                        Map.of(
                            "postId",
                            id,
                            "postTitle",
                            post.getTitle(),
                            "authorId",
                            post.getAuthorId(),
                            "reviewState",
                            "NORMAL",
                            "labels",
                            List.of()
                        )
                    );
                }
            } else {
                posts.deleteForReview(id);
                jdbc.update("UPDATE post_ai_feature SET delete_state=1 WHERE post_id=?", id);
            }
        } else if ("COMMENT".equals(kind)) {
            if (approved) {
                jdbc.update(
                    "UPDATE comment SET review_state='NORMAL',update_time=update_time WHERE id=? AND delete_state=0",
                    id
                );
                jdbc.update("UPDATE comment_ai_review SET manual_review=1 WHERE comment_id=?", id);
                var rows = jdbc.queryForList(
                    "SELECT c.post_id,c.author_id,p.title FROM comment c JOIN post p ON p.id=c.post_id WHERE c.id=? AND c.delete_state=0",
                    id
                );
                if (!rows.isEmpty()) {
                    var row = rows.get(0);
                    outbox.enqueue(
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
                            "",
                            "reviewState",
                            "NORMAL",
                            "labels",
                            List.of()
                        )
                    );
                    events.publishEvent(new AiResultService.ReviewNotification());
                }
            } else {
                comments.deleteForReview(id);
                jdbc.update("UPDATE comment_ai_review SET delete_state=1 WHERE comment_id=?", id);
            }
        } else throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
    }
}

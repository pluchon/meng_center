package com.pluchon.meng.im.mq;

import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.api.user.event.UserApprovedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.im.entity.Channel;
import com.pluchon.meng.im.entity.ImRows;
import com.pluchon.meng.im.entity.NoticeType;
import com.pluchon.meng.im.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 将生产域快照映射为通知，不回查生产域
@Component
public class NotificationListener {

    @Autowired
    private NotificationService notices;

    @RabbitListener(queues = "meng.im.comment.created")
    public void comment(EventEnvelope<CommentCreatedEvent> event) {
        check(event, "comment.created");
        var body = event.body();
        var row = new ImRows.Notice();
        row.setReceiverId(body.targetUserId());
        row.setActorId(body.authorId());
        row.setChannel(Channel.COMMENT);
        row.setSource("POST");
        row.setType(
            body.parentCommentId() == null
                ? NoticeType.POST_COMMENT
                : NoticeType.COMMENT_REPLY
        );
        row.setPostId(body.postId());
        row.setPostTitle(body.postTitle());
        row.setCommentId(body.commentId());
        row.setExcerpt(body.excerpt());
        row.setOriginalExcerpt(body.originalExcerpt());
        row.setDedupeKey("comment:" + body.commentId());
        notices.receive(row);
        mentions(body);
    }

    // 被 @ 的人各收一条提到了你；已经因为被回复收到通知的人不再重复提醒
    private void mentions(CommentCreatedEvent body) {
        if (body.mentionedUserIds() == null) return;
        for (var userId : body.mentionedUserIds()) {
            if (userId.equals(body.targetUserId())) continue;
            var row = new ImRows.Notice();
            row.setReceiverId(userId);
            row.setActorId(body.authorId());
            row.setChannel(Channel.COMMENT);
            row.setSource("POST");
            row.setType(NoticeType.COMMENT_MENTION);
            row.setPostId(body.postId());
            row.setPostTitle(body.postTitle());
            row.setCommentId(body.commentId());
            row.setExcerpt(body.excerpt());
            row.setDedupeKey("mention:" + body.commentId() + ":" + userId);
            notices.receive(row);
        }
    }

    @RabbitListener(
        queues = {
            "meng.im.post.liked",
            "meng.im.post.favorited",
            "meng.im.comment.liked",
        }
    )
    public void interaction(EventEnvelope<ContentInteractionEvent> event) {
        var body = event.body();
        check(event, body.eventType());
        var row = new ImRows.Notice();
        row.setReceiverId(body.authorId());
        row.setActorId(body.actorId());
        row.setChannel(Channel.LIKE);
        row.setSource("POST");
        row.setType(
            switch (event.eventType()) {
                case "post.liked" -> NoticeType.POST_LIKED;
                case "post.favorited" -> NoticeType.POST_FAVORITED;
                case "comment.liked" -> NoticeType.COMMENT_LIKED;
                default -> throw new IllegalArgumentException(
                    "Wrong interaction type"
                );
            }
        );
        row.setPostId(body.postId());
        row.setPostTitle(body.postTitle());
        row.setCommentId(body.commentId());
        row.setExcerpt(body.excerpt());
        row.setDedupeKey(
            event.eventType() +
                ":" +
                (body.commentId() == null ? body.postId() : body.commentId()) +
                ":" +
                body.actorId()
        );
        notices.receive(row);
    }

    @RabbitListener(queues = "meng.im.growth.level-changed")
    public void level(EventEnvelope<LevelChangedEvent> event) {
        check(event, "growth.level-changed");
        var row = new ImRows.Notice();
        row.setReceiverId(event.body().userId());
        row.setChannel(Channel.SYSTEM);
        row.setCategory("GROWTH");
        row.setType(NoticeType.LEVEL_CHANGED);
        row.setLevel(event.body().level());
        row.setDedupeKey("level:" + event.eventId());
        notices.receive(row);
    }

    @RabbitListener(queues = "meng.im.user.approved")
    public void approved(EventEnvelope<UserApprovedEvent> event) {
        check(event, "user.approved");
        var row = new ImRows.Notice();
        row.setReceiverId(event.body().userId());
        row.setChannel(Channel.SYSTEM);
        row.setCategory("APPROVAL");
        row.setType(NoticeType.USER_APPROVED);
        row.setDedupeKey("approved:" + event.body().userId());
        notices.receive(row);
    }

    private static void check(EventEnvelope<?> event, String expected) {
        if (
            !expected.equals(event.eventType()) ||
            event.eventId() == null ||
            event.body() == null
        ) throw new IllegalArgumentException("Wrong event envelope");
    }
}

package com.pluchon.meng.growth.mq;

import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.service.GrowthService;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 接收帖子和评论事件并发放经验
@Component
public class GrowthEventListener {

    // 帖子发布事件消费队列
    public static final String POST_QUEUE = "meng.growth.post.published";

    // 评论发布事件消费队列
    public static final String COMMENT_QUEUE = "meng.growth.comment.created";

    // 帖子被喜欢事件消费队列
    public static final String LIKE_QUEUE = "meng.growth.post.liked";

    // 帖子被喜欢的事件名
    public static final String POST_LIKED = "post.liked";

    // 成长业务操作
    @Autowired
    private GrowthService growth;

    @RabbitListener(queues = POST_QUEUE)
    public void post(EventEnvelope<PostPublishedEvent> e) {
        if (!PostPublishedEvent.EVENT_TYPE.equals(e.eventType()))
            throw new IllegalArgumentException("Wrong event type");
        growth.earned(
                e.body().authorId(),
                ExpSource.POST,
                Long.toString(e.body().postId()),
                e.occurredAt());
    }

    @RabbitListener(queues = COMMENT_QUEUE)
    public void comment(EventEnvelope<CommentCreatedEvent> e) {
        if (!CommentCreatedEvent.EVENT_TYPE.equals(e.eventType()))
            throw new IllegalArgumentException("Wrong event type");
        growth.earned(
                e.body().authorId(),
                ExpSource.COMMENT,
                Long.toString(e.body().commentId()),
                e.occurredAt());
    }

    // 点赞别人的帖子给点赞的人发经验；同一篇帖子只算一次，给自己点赞不算
    @RabbitListener(queues = LIKE_QUEUE)
    public void like(EventEnvelope<ContentInteractionEvent> e) {
        if (!POST_LIKED.equals(e.eventType()))
            throw new IllegalArgumentException("Wrong event type");
        if (e.body().actorId() == e.body().authorId()) return;
        growth.earned(
                e.body().actorId(),
                ExpSource.LIKE,
                Long.toString(e.body().postId()),
                e.occurredAt());
    }
}

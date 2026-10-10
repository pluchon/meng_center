package com.pluchon.meng.growth.mq;

import com.pluchon.meng.api.content.event.*;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.growth.entity.enums.ExpSource;
import com.pluchon.meng.growth.service.GrowthService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GrowthEventListener {

    public static final String POST_QUEUE = "meng.growth.post.published",
        COMMENT_QUEUE = "meng.growth.comment.created";
    private final GrowthService growth;

    @RabbitListener(queues = POST_QUEUE)
    public void post(EventEnvelope<PostPublishedEvent> e) {
        if (
            !PostPublishedEvent.EVENT_TYPE.equals(e.eventType())
        ) throw new IllegalArgumentException("Wrong event type");
        growth.earned(
            e.body().authorId(),
            ExpSource.POST,
            Long.toString(e.body().postId()),
            e.occurredAt()
        );
    }

    @RabbitListener(queues = COMMENT_QUEUE)
    public void comment(EventEnvelope<CommentCreatedEvent> e) {
        if (
            !CommentCreatedEvent.EVENT_TYPE.equals(e.eventType())
        ) throw new IllegalArgumentException("Wrong event type");
        growth.earned(
            e.body().authorId(),
            ExpSource.COMMENT,
            Long.toString(e.body().commentId()),
            e.occurredAt()
        );
    }
}

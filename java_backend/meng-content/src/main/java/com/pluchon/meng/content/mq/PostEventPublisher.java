package com.pluchon.meng.content.mq;

import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.UUID;

// 事务提交后发布帖子领域事件
@Component
public class PostEventPublisher {

    @TransactionalEventListener
    public void committed(CommentCreatedEvent comment) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
                CommentCreatedEvent.EXCHANGE,
                CommentCreatedEvent.EVENT_TYPE,
                new EventEnvelope<>(
                        id, CommentCreatedEvent.EVENT_TYPE, clock.instant(), id, comment));
    }

    // 领域事件发送组件
    @Autowired
    private RabbitTemplate rabbit;

    // 业务时钟
    @Autowired
    private Clock clock;

    @TransactionalEventListener
    public void committed(PostPublishedEvent post) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
                PostPublishedEvent.EXCHANGE,
                PostPublishedEvent.EVENT_TYPE,
                new EventEnvelope<>(id, PostPublishedEvent.EVENT_TYPE, clock.instant(), id, post));
    }
}

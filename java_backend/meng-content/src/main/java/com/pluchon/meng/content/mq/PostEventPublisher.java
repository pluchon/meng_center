package com.pluchon.meng.content.mq;

import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 事务提交后发布帖子领域事件
@Component
public class PostEventPublisher {

    // 互动关系真正创建后才发布通知事件
    @TransactionalEventListener
    public void committed(ContentInteractionEvent interaction) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
            ContentInteractionEvent.EXCHANGE,
            interaction.eventType(),
            new EventEnvelope<>(id, interaction.eventType(), clock.instant(), id, interaction)
        );
    }

    @TransactionalEventListener
    public void committed(CommentCreatedEvent comment) {
        aiEvents.dispatchAsync();
    }

    // 领域事件发送组件
    @Autowired
    private RabbitTemplate rabbit;

    // 业务时钟
    @Autowired
    private Clock clock;

    @Autowired
    private com.pluchon.meng.content.service.AiEventService aiEvents;

    @TransactionalEventListener
    public void committed(PostPublishedEvent post) {
        aiEvents.dispatchAsync();
    }
}

package com.pluchon.meng.content.mq;

import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PostEventPublisher {

    private final RabbitTemplate rabbit;
    private final Clock clock;

    @TransactionalEventListener
    public void committed(PostPublishedEvent post) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
            PostPublishedEvent.EXCHANGE,
            PostPublishedEvent.EVENT_TYPE,
            new EventEnvelope<>(
                id,
                PostPublishedEvent.EVENT_TYPE,
                clock.instant(),
                id,
                post
            )
        );
    }
}

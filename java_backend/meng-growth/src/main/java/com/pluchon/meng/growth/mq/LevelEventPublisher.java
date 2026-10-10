package com.pluchon.meng.growth.mq;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class LevelEventPublisher {

    private final RabbitTemplate rabbit;
    private final Clock clock;

    @TransactionalEventListener
    public void committed(LevelChangedEvent body) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
            LevelChangedEvent.EXCHANGE,
            LevelChangedEvent.EVENT_TYPE,
            new EventEnvelope<>(
                id,
                LevelChangedEvent.EVENT_TYPE,
                clock.instant(),
                id,
                body
            )
        );
    }
}

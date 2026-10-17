package com.pluchon.meng.companion.mq;

import com.pluchon.meng.api.companion.event.StageReachedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 事务成功后才发布阶段变化，通知不携带私人聊天内容
@Component
public class StagePublisher {
    @Autowired
    private RabbitTemplate rabbit;
    @Autowired
    private Clock clock;

    @TransactionalEventListener
    public void committed(StageReachedEvent body) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(StageReachedEvent.EXCHANGE, StageReachedEvent.EVENT_TYPE,
            new EventEnvelope<>(id, StageReachedEvent.EVENT_TYPE, clock.instant(), id, body));
    }
}

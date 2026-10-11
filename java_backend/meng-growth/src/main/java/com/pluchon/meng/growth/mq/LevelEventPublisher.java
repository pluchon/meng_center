package com.pluchon.meng.growth.mq;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.UUID;

// 事务提交后发布等级变化事件
@Component
public class LevelEventPublisher {

    // 领域事件发送组件
    @Autowired
    private RabbitTemplate rabbit;

    // 业务时钟
    @Autowired
    private Clock clock;

    @TransactionalEventListener
    public void committed(LevelChangedEvent body) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
                LevelChangedEvent.EXCHANGE,
                LevelChangedEvent.EVENT_TYPE,
                new EventEnvelope<>(id, LevelChangedEvent.EVENT_TYPE, clock.instant(), id, body));
    }
}

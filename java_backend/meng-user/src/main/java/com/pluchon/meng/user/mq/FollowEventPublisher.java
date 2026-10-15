package com.pluchon.meng.user.mq;

import com.pluchon.meng.api.user.event.UserFollowedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 关注事务提交之后才对外发消息，回滚的关注不会留下通知
@Component
public final class FollowEventPublisher {

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private Clock clock;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(FollowCommitted follow) {
        String id = UUID.randomUUID().toString();
        rabbit.convertAndSend(
            UserFollowedEvent.EXCHANGE,
            UserFollowedEvent.EVENT_TYPE,
            new EventEnvelope<>(
                id,
                UserFollowedEvent.EVENT_TYPE,
                clock.instant(),
                id,
                new UserFollowedEvent(follow.followerId(), follow.followeeId())
            )
        );
    }
}

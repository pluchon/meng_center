package com.pluchon.meng.user.mq;

import com.pluchon.meng.api.user.event.UserApprovedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 审核事务提交之后才对外发消息，回滚的审核不会留下事件或通知
@Component
public final class ReviewEventPublisher {

    // 审核通知一天内没发出去就不再发
    private static final Duration NOTIFICATION_TTL = Duration.ofDays(1);

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private Clock clock;

    // 通过时给下游发 user.approved 事件；有联系方式的账号再发一条通知
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void committed(ReviewCommitted review) {
        if (review.kind() == NotificationKind.APPROVED) {
            String id = UUID.randomUUID().toString();
            rabbit.convertAndSend(
                UserApprovedEvent.EXCHANGE,
                UserApprovedEvent.EVENT_TYPE,
                new EventEnvelope<>(
                    id,
                    UserApprovedEvent.EVENT_TYPE,
                    clock.instant(),
                    id,
                    new UserApprovedEvent(review.userId())
                )
            );
        }
        if (review.recipientCipher() != null) {
            var command = new DeliveryCommand(
                UUID.randomUUID().toString(),
                review.channel(),
                review.recipientCipher(),
                review.kind(),
                review.encryptedReason(),
                clock.instant().plus(NOTIFICATION_TTL)
            );
            rabbit.convertAndSend(UserMqNames.INTERNAL_EXCHANGE, UserMqNames.NOTIFICATION_ROUTE, command);
        }
    }
}

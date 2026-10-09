package com.pluchon.meng.user.mq;

import com.pluchon.meng.api.user.event.UserApprovedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.user.config.UserProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;

@Component
public final class ReviewEventPublisher {

    private final RabbitTemplate rabbit;
    private final Clock clock;

    public ReviewEventPublisher(RabbitTemplate rabbit, Clock clock) {
        this.rabbit = rabbit;
        this.clock = clock;
    }

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
                clock.instant().plus(Duration.ofDays(1))
            );
            rabbit.convertAndSend(UserMqNames.INTERNAL_EXCHANGE, UserMqNames.NOTIFICATION_ROUTE, command);
        }
    }
}

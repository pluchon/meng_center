package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.client.*;
import com.pluchon.meng.user.service.security.PiiCipher;
import java.time.Clock;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public final class DeliveryListener {

    private final PiiCipher cipher;
    private final com.pluchon.meng.user.service.security.ContactService contacts;
    private final MailDeliveryClient mail;
    private final DomesticSmsClient domestic;
    private final InternationalSmsClient international;
    private final Clock clock;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private static final String DELIVERY_PREFIX = "meng:delivery:";
    private static final String DELIVERED = "delivered";

    public DeliveryListener(
        PiiCipher cipher,
        com.pluchon.meng.user.service.security.ContactService contacts,
        MailDeliveryClient mail,
        DomesticSmsClient domestic,
        InternationalSmsClient international,
        Clock clock,
        org.springframework.data.redis.core.StringRedisTemplate redis
    ) {
        this.cipher = cipher;
        this.contacts = contacts;
        this.mail = mail;
        this.domestic = domestic;
        this.international = international;
        this.clock = clock;
        this.redis = redis;
    }

    private void deliver(DeliveryCommand command) throws Exception {
        if (command.expiresAt().isBefore(clock.instant())) throw new IllegalStateException("Delivery expired");
        String recipient = cipher.decrypt(command.recipientCipher());
        String content = cipher.decrypt(command.contentCipher());
        switch (command.channel()) {
            case EMAIL -> mail.send(recipient, command.kind(), content);
            case SMS -> {
                if (
                    com.pluchon.meng.user.entity.enums.PhoneRegion.DOMESTIC.equals(contacts.countryCode(recipient))
                ) domestic.send(recipient, command.kind(), content);
                else international.send(recipient, command.kind(), content);
            }
        }
    }

    @RabbitListener(queues = UserMqNames.CODE_QUEUE)
    public String code(DeliveryCommand command) {
        try {
            deliver(command);
            return UserMqNames.DELIVERY_OK;
        } catch (Exception exception) {
            return UserMqNames.DELIVERY_FAILED;
        }
    }

    @RabbitListener(queues = UserMqNames.NOTIFICATION_QUEUE)
    public void review(DeliveryCommand command) {
        String key = DELIVERY_PREFIX + command.deliveryId();
        if (DELIVERED.equals(redis.opsForValue().get(key))) return;
        if (
            !Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "processing", java.time.Duration.ofMinutes(1)))
        ) throw new IllegalStateException("Notification is already being processed.");
        try {
            deliver(command);
            redis.opsForValue().set(key, DELIVERED, java.time.Duration.ofDays(2));
        } catch (Exception exception) {
            redis.delete(key);
            throw new AmqpRejectAndDontRequeueException("Review notification delivery failed");
        }
    }
}

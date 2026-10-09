package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.client.DomesticSmsClient;
import com.pluchon.meng.user.client.InternationalSmsClient;
import com.pluchon.meng.user.client.MailDeliveryClient;
import com.pluchon.meng.user.entity.enums.PhoneRegion;
import com.pluchon.meng.user.service.security.ContactService;
import com.pluchon.meng.user.service.security.PiiCipher;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// 邮件和短信的实际发送都在这里，请求线程不直接碰外部通道
@Component
public final class DeliveryListener {

    private static final String DELIVERY_PREFIX = "meng:delivery:";
    private static final String PROCESSING = "processing";
    private static final String DELIVERED = "delivered";
    // 处理中的占位只保留很短时间，消费者中途挂掉后别的消费者还能接手
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(1);
    // 已送达的标记保留两天，足够盖住重复投递
    private static final Duration DELIVERED_TTL = Duration.ofDays(2);

    @Autowired
    private PiiCipher cipher;

    @Autowired
    private ContactService contacts;

    @Autowired
    private MailDeliveryClient mail;

    @Autowired
    private DomesticSmsClient domestic;

    @Autowired
    private InternationalSmsClient international;

    @Autowired
    private Clock clock;

    @Autowired
    private StringRedisTemplate redis;

    // 过期的指令不再发送；短信按完整区号选国内或国际通道
    private void deliver(DeliveryCommand command) throws Exception {
        if (command.expiresAt().isBefore(clock.instant())) {
            throw new IllegalStateException("Delivery expired");
        }
        String recipient = cipher.decrypt(command.recipientCipher());
        String content = cipher.decrypt(command.contentCipher());
        switch (command.channel()) {
            case EMAIL -> mail.send(recipient, command.kind(), content);
            case SMS -> {
                if (PhoneRegion.DOMESTIC.equals(contacts.countryCode(recipient))) {
                    domestic.send(recipient, command.kind(), content);
                } else {
                    international.send(recipient, command.kind(), content);
                }
            }
        }
    }

    // 验证码：把成败作为回执交还给等在请求线程里的调用方，不重试
    @RabbitListener(queues = UserMqNames.CODE_QUEUE)
    public String code(DeliveryCommand command) {
        try {
            deliver(command);
            return UserMqNames.DELIVERY_OK;
        } catch (Exception exception) {
            return UserMqNames.DELIVERY_FAILED;
        }
    }

    // 审核通知：按投递编号去重，失败交给重试，最终进死信队列
    @RabbitListener(queues = UserMqNames.NOTIFICATION_QUEUE)
    public void review(DeliveryCommand command) {
        String key = DELIVERY_PREFIX + command.deliveryId();
        if (DELIVERED.equals(redis.opsForValue().get(key))) {
            return;
        }
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, PROCESSING, PROCESSING_TTL))) {
            throw new IllegalStateException("Notification is already being processed.");
        }
        try {
            deliver(command);
            redis.opsForValue().set(key, DELIVERED, DELIVERED_TTL);
        } catch (Exception exception) {
            redis.delete(key);
            throw new AmqpRejectAndDontRequeueException("Review notification delivery failed");
        }
    }
}

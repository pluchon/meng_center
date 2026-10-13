package com.pluchon.meng.user.service;

import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.mq.*;
import com.pluchon.meng.user.service.security.PiiCipher;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@lombok.extern.slf4j.Slf4j
public class ContactChangeNotification {

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private PiiCipher cipher;

    @Autowired
    private Clock clock;

    public void changed(String oldEmail) {
        var command = new DeliveryCommand(
            UUID.randomUUID().toString(),
            ContactChannel.EMAIL,
            oldEmail,
            NotificationKind.CONTACT_CHANGED,
            cipher.encrypt(
                "你的账号邮箱已更换。若非本人操作，请及时修改密码。"
            ),
            clock.instant().plus(Duration.ofHours(1))
        );
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        rabbit.convertAndSend(
                            UserMqNames.INTERNAL_EXCHANGE,
                            UserMqNames.NOTIFICATION_ROUTE,
                            command
                        );
                    } catch (org.springframework.amqp.AmqpException failure) {
                        log.error(
                            "Failed to enqueue contact-change notification {}",
                            command.deliveryId(),
                            failure
                        );
                    }
                }
            }
        );
    }
}

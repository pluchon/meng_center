package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.entity.enums.ContactChannel;
import java.time.Instant;

// 一次发送指令，收件人和内容都是密文
public record DeliveryCommand(
    String deliveryId,
    ContactChannel channel,
    String recipientCipher,
    NotificationKind kind,
    String contentCipher,
    Instant expiresAt
) {}

package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.entity.enums.ContactChannel;
import java.time.Instant;

public record DeliveryCommand(
    String deliveryId,
    ContactChannel channel,
    String recipientCipher,
    NotificationKind kind,
    String contentCipher,
    Instant expiresAt
) {}

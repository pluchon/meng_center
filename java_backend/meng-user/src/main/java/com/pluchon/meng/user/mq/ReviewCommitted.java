package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.entity.enums.ContactChannel;

public record ReviewCommitted(
    long userId,
    ContactChannel channel,
    String recipientCipher,
    NotificationKind kind,
    String encryptedReason
) {}

package com.pluchon.meng.user.mq;

import com.pluchon.meng.user.entity.enums.ContactChannel;

// 审核已写库的进程内事件，事务提交后才会被处理
public record ReviewCommitted(
    long userId,
    ContactChannel channel,
    String recipientCipher,
    NotificationKind kind,
    String encryptedReason
) {}

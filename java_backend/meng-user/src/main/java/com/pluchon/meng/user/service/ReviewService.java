package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.user.entity.enums.*;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.mq.*;
import com.pluchon.meng.user.service.security.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final UserMapper mapper;
    private final AuthorizationService authorization;
    private final ApplicationEventPublisher events;
    private final PiiCipher cipher;

    public ReviewService(
        UserMapper mapper,
        AuthorizationService authorization,
        ApplicationEventPublisher events,
        PiiCipher cipher
    ) {
        this.mapper = mapper;
        this.authorization = authorization;
        this.events = events;
        this.cipher = cipher;
    }

    @Transactional
    public void approve(long id) {
        review(id, null, UserStatus.NORMAL, NotificationKind.APPROVED);
    }

    @Transactional
    public void reject(long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 200) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        review(id, reason, UserStatus.REJECTED, NotificationKind.REJECTED);
    }

    private void review(long id, String reason, UserStatus status, NotificationKind kind) {
        authorization.requireAdmin();
        var user = mapper.lockById(id);
        if (user == null || user.getStatus() != UserStatus.PENDING) throw new BusinessException(
            CommonErrorCode.CONFLICT
        );
        user.setStatus(status);
        user.setRejectReason(reason);
        mapper.updateById(user);
        ContactChannel channel = user.getEmailCipher() != null ? ContactChannel.EMAIL : ContactChannel.SMS;
        String recipient = user.getEmailCipher() != null ? user.getEmailCipher() : user.getPhoneCipher();
        events.publishEvent(
            new ReviewCommitted(id, channel, recipient, kind, cipher.encrypt(reason == null ? "" : reason))
        );
    }
}

package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.mq.NotificationKind;
import com.pluchon.meng.user.mq.ReviewCommitted;
import com.pluchon.meng.user.service.security.AuthorizationService;
import com.pluchon.meng.user.service.security.PiiCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 管理员审核注册申请：只允许从待审核出发，事务提交后再发事件和通知
@Service
public class ReviewService {

    private static final int REASON_MAX_LENGTH = 200;

    @Autowired
    private UserMapper mapper;

    @Autowired
    private AuthorizationService authorization;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private PiiCipher cipher;

    // 通过申请
    @Transactional(rollbackFor = Exception.class)
    public void approve(long id) {
        review(id, null, UserStatus.NORMAL, NotificationKind.APPROVED);
    }

    // 拒绝申请，必须给出原因
    @Transactional(rollbackFor = Exception.class)
    public void reject(long id, String reason) {
        if (reason == null || reason.isBlank() || reason.length() > REASON_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        }
        review(id, reason, UserStatus.REJECTED, NotificationKind.REJECTED);
    }

    // 审核结果随事务提交后发出，通知走注册时用的那个渠道
    private void review(long id, String reason, UserStatus status, NotificationKind kind) {
        authorization.requireAdmin();
        UserEntity user = mapper.lockById(id);
        if (user == null || user.getStatus() != UserStatus.PENDING) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        user.setStatus(status);
        user.setRejectReason(reason);
        mapper.updateById(user);
        boolean byEmail = user.getEmailCipher() != null;
        events.publishEvent(
            new ReviewCommitted(
                id,
                byEmail ? ContactChannel.EMAIL : ContactChannel.SMS,
                byEmail ? user.getEmailCipher() : user.getPhoneCipher(),
                kind,
                cipher.encrypt(reason == null ? "" : reason)
            )
        );
    }
}

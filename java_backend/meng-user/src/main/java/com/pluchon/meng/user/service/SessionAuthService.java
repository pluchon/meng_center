package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.dto.*;
import com.pluchon.meng.user.controller.vo.RejectionVO;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionAuthService {

    private final UserMapper mapper;
    private final PasswordService passwords;
    private final RateLimitService limits;
    private final CaptchaService captcha;
    private final SessionService sessions;
    private final UserProperties properties;
    private final AuthorizationService authorization;

    public SessionAuthService(
        UserMapper mapper,
        PasswordService passwords,
        RateLimitService limits,
        CaptchaService captcha,
        SessionService sessions,
        UserProperties properties,
        AuthorizationService authorization
    ) {
        this.mapper = mapper;
        this.passwords = passwords;
        this.limits = limits;
        this.captcha = captcha;
        this.sessions = sessions;
        this.properties = properties;
        this.authorization = authorization;
    }

    @Transactional
    public String login(LoginDTO request, String ip) {
        String name = RegistrationService.username(request.username());
        int failures = limits.loginFailures(name);
        if (failures >= properties.limits().loginMaxFailures()) throw new BusinessException(
            CommonErrorCode.RATE_LIMITED
        );
        captcha.consume(request.ticket(), ip);
        var user = mapper.lockByUsername(name);
        if (!passwords.matches(request.password(), user == null ? null : user.getPasswordHash())) {
            limits.failedLogin(name);
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        limits.clearLogin(name);
        if (user.getStatus() == UserStatus.PENDING) throw new BusinessException(UserErrorCode.PENDING);
        if (user.getStatus() == UserStatus.REJECTED) throw new BusinessException(
            UserErrorCode.REJECTED,
            new RejectionVO(user.getRejectReason())
        );
        return sessions.create(user);
    }

    public void logout() {
        var identity = UserContext.require();
        sessions.revoke(identity.userId(), identity.sessionId());
    }

    @Transactional
    public void changePassword(PasswordChangeDTO request) {
        var identity = UserContext.require();
        authorization.current();
        var user = mapper.lockById(identity.userId());
        if (!passwords.matches(request.oldPassword(), user.getPasswordHash())) throw new BusinessException(
            UserErrorCode.INVALID_CREDENTIALS
        );
        user.setPasswordHash(passwords.hash(request.password(), request.confirmPassword()));
        mapper.updateById(user);
        sessions.revokeAll(user.getId());
    }
}

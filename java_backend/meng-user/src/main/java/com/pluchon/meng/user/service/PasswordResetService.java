package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.dto.PasswordResetDTO;
import com.pluchon.meng.user.entity.enums.*;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final String RATE_SCOPE = "password-reset";
    private final ContactService contacts;
    private final VerificationCodeService codes;
    private final CaptchaService captcha;
    private final RateLimitService limits;
    private final UserProperties properties;
    private final UserMapper mapper;
    private final PasswordService passwords;
    private final SessionService sessions;

    public PasswordResetService(
        ContactService contacts,
        VerificationCodeService codes,
        CaptchaService captcha,
        RateLimitService limits,
        UserProperties properties,
        UserMapper mapper,
        PasswordService passwords,
        SessionService sessions
    ) {
        this.contacts = contacts;
        this.codes = codes;
        this.captcha = captcha;
        this.limits = limits;
        this.properties = properties;
        this.mapper = mapper;
        this.passwords = passwords;
        this.sessions = sessions;
    }

    @Transactional
    public void reset(PasswordResetDTO request, String ip) {
        var contact = contacts.normalize(
            request.email(),
            request.countryCode(),
            request.phone()
        );
        captcha.consume(request.ticket(), ip);
        limits.charge(
            RATE_SCOPE,
            contact.hash(),
            properties.verification().maxErrors(),
            properties.verification().codeTtl()
        );
        String hash = passwords.hash(
            request.password(),
            request.confirmPassword()
        );
        try {
            codes.consume(
                contact,
                request.code(),
                VerificationPurpose.PASSWORD_RESET
            );
        } catch (BusinessException exception) {
            throw new BusinessException(UserErrorCode.RESET_FAILED);
        }
        var user =
            contact.channel() == ContactChannel.EMAIL
                ? mapper.lockByEmailHash(contact.hash())
                : mapper.lockByPhoneHash(contact.hash());
        if (
            user == null || user.getRole() != UserRole.USER
        ) throw new BusinessException(UserErrorCode.RESET_FAILED);
        user.setPasswordHash(hash);
        mapper.updateById(user);
        sessions.revokeAll(user.getId());
        limits.clearLogin(user.getUsername());
    }
}

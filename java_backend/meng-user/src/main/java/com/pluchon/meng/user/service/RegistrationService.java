package com.pluchon.meng.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.dto.*;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.*;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.*;
import java.time.Duration;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserMapper mapper;
    private final ContactService contacts;
    private final PasswordService passwords;
    private final VerificationCodeService codes;
    private final CaptchaService captcha;
    private final RateLimitService limits;
    private final UserProperties properties;

    public RegistrationService(
        UserMapper mapper,
        ContactService contacts,
        PasswordService passwords,
        VerificationCodeService codes,
        CaptchaService captcha,
        RateLimitService limits,
        UserProperties properties
    ) {
        this.mapper = mapper;
        this.contacts = contacts;
        this.passwords = passwords;
        this.codes = codes;
        this.captcha = captcha;
        this.limits = limits;
        this.properties = properties;
    }

    public static String username(String input) {
        String value = input.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9_]{3,32}")) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        return value;
    }

    @Transactional
    public void register(RegistrationDTO request, String ip) {
        if (!properties.legal().published()) throw new BusinessException(UserErrorCode.LEGAL_NOT_PUBLISHED);
        if (
            !properties.legal().termsVersion().equals(request.termsVersion()) ||
            !properties.legal().privacyVersion().equals(request.privacyVersion())
        ) throw new BusinessException(UserErrorCode.LEGAL_VERSION_CHANGED);
        captcha.consume(request.ticket(), ip);
        String name = username(request.username());
        String password = passwords.hash(request.password(), request.confirmPassword());
        var contact = contacts.normalize(request.email(), request.countryCode(), request.phone());
        if (
            mapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getUsername, name)) > 0
        ) throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        com.baomidou.mybatisplus.core.toolkit.support.SFunction<UserEntity, String> field =
            contact.channel() == ContactChannel.EMAIL
                ? UserEntity::getEmailHash
                : (com.baomidou.mybatisplus.core.toolkit.support.SFunction<
                      UserEntity,
                      String
                  >) UserEntity::getPhoneHash;
        if (
            mapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(field, contact.hash())) > 0
        ) throw new BusinessException(UserErrorCode.CONTACT_TAKEN);
        codes.consume(contact, request.code());
        limits.charge("registration-ip", ip, properties.limits().registrationIpDailyMax(), Duration.ofDays(1));
        var user = new UserEntity();
        user.setUsername(name);
        user.setPasswordHash(password);
        user.setTermsVersion(request.termsVersion());
        user.setPrivacyVersion(request.privacyVersion());
        user.setAgreedAt(java.time.LocalDateTime.now(java.time.Clock.systemUTC()));
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING);
        user.setApplyReason(request.applyReason());
        user.setLevel(0);
        user.setDeleteState(0);
        if (contact.channel() == ContactChannel.EMAIL) {
            user.setEmailCipher(contact.cipher());
            user.setEmailHash(contact.hash());
        } else {
            user.setPhoneCipher(contact.cipher());
            user.setPhoneHash(contact.hash());
        }
        try {
            mapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
    }

    @Transactional
    public void reapply(ReapplicationDTO request, String ip) {
        String name = username(request.username());
        if (limits.loginFailures(name) >= properties.limits().loginMaxFailures())
            throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        captcha.consume(request.ticket(), ip);
        var user = mapper.lockByUsername(name);
        if (
            !passwords.matches(request.password(), user == null ? null : user.getPasswordHash())
        ) {
            limits.failedLogin(name);
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        limits.clearLogin(name);
        if (user.getStatus() != UserStatus.REJECTED) throw new BusinessException(CommonErrorCode.CONFLICT);
        limits.charge("reapplication", name, properties.limits().reapplicationDailyMax(), Duration.ofDays(1));
        user.setStatus(UserStatus.PENDING);
        user.setApplyReason(request.applyReason());
        user.setRejectReason(null);
        // Explicit null update is required; MyBatis-Plus normally skips null entity fields.
        mapper.update(
            null,
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, user.getId())
                .set(UserEntity::getStatus, UserStatus.PENDING)
                .set(UserEntity::getApplyReason, request.applyReason())
                .set(UserEntity::getRejectReason, null)
        );
    }
}

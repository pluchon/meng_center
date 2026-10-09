package com.pluchon.meng.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.dto.ReapplicationDTO;
import com.pluchon.meng.user.controller.dto.RegistrationDTO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.ContactService;
import com.pluchon.meng.user.service.security.PasswordService;
import com.pluchon.meng.user.service.security.RateLimitService;
import com.pluchon.meng.user.service.security.UsernamePolicy;
import com.pluchon.meng.user.service.security.VerificationCodeService;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 注册申请与被拒后的重新申请
@Service
public class RegistrationService {

    private static final String REGISTRATION_IP_SCOPE = "registration-ip";
    private static final String REAPPLICATION_SCOPE = "reapplication";

    @Autowired
    private UserMapper mapper;

    @Autowired
    private CredentialService credentials;

    @Autowired
    private ContactService contacts;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private VerificationCodeService codes;

    @Autowired
    private CaptchaService captcha;

    @Autowired
    private RateLimitService limits;

    @Autowired
    private UserProperties properties;

    @Autowired
    private Clock clock;

    // 提交注册申请，入库后是待审核状态。验证码放在重复检查之后才消耗，重名时不白白作废
    @Transactional(rollbackFor = Exception.class)
    public void register(RegistrationDTO request, String ip) {
        requireCurrentLegal(request);
        captcha.consume(request.ticket(), ip);
        String name = UsernamePolicy.normalize(request.username());
        String password = passwords.hash(request.password(), request.confirmPassword());
        ContactService.Contact contact = contacts.normalize(request.email(), request.countryCode(), request.phone());
        boolean byEmail = contact.channel() == ContactChannel.EMAIL;
        if (mapper.exists(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, name))) {
            throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        }
        var sameContact = Wrappers.<UserEntity>lambdaQuery()
            .eq(byEmail, UserEntity::getEmailHash, contact.hash())
            .eq(!byEmail, UserEntity::getPhoneHash, contact.hash());
        if (mapper.exists(sameContact)) {
            throw new BusinessException(UserErrorCode.CONTACT_TAKEN);
        }
        codes.consume(contact, request.code(), VerificationPurpose.REGISTRATION);
        limits.chargeDaily(REGISTRATION_IP_SCOPE, ip, properties.limits().registrationIpDailyMax());
        // 只挑申请需要的字段写入，不把请求整体复制进实体
        UserEntity user = new UserEntity();
        user.setUsername(name);
        user.setPasswordHash(password);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.PENDING);
        user.setApplyReason(request.applyReason());
        user.setTermsVersion(request.termsVersion());
        user.setPrivacyVersion(request.privacyVersion());
        user.setAgreedAt(LocalDateTime.now(clock));
        if (byEmail) {
            user.setEmailCipher(contact.cipher());
            user.setEmailHash(contact.hash());
        } else {
            user.setPhoneCipher(contact.cipher());
            user.setPhoneHash(contact.hash());
        }
        try {
            mapper.insert(user);
        } catch (DuplicateKeyException exception) {
            // 并发下两个请求同时通过了上面的检查，由唯一索引兜底
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
    }

    // 协议还没发布不开放注册；用户同意的版本必须是当前版本
    private void requireCurrentLegal(RegistrationDTO request) {
        var legal = properties.legal();
        if (!legal.published()) {
            throw new BusinessException(UserErrorCode.LEGAL_NOT_PUBLISHED);
        }
        if (
            !legal.termsVersion().equals(request.termsVersion()) ||
            !legal.privacyVersion().equals(request.privacyVersion())
        ) {
            throw new BusinessException(UserErrorCode.LEGAL_VERSION_CHANGED);
        }
    }

    // 被拒后重新申请：先校验用户名和密码，只允许从已拒绝回到待审核
    @Transactional(rollbackFor = Exception.class)
    public void reapply(ReapplicationDTO request, String ip) {
        UserEntity user = credentials.authenticate(request.username(), request.password(), request.ticket(), ip);
        if (user.getStatus() != UserStatus.REJECTED) {
            throw new BusinessException(CommonErrorCode.CONFLICT);
        }
        limits.chargeDaily(REAPPLICATION_SCOPE, user.getUsername(), properties.limits().reapplicationDailyMax());
        // 按实体更新会跳过空值，清空拒绝原因必须显式写出来
        mapper.update(
            null,
            Wrappers.<UserEntity>lambdaUpdate()
                .eq(UserEntity::getId, user.getId())
                .set(UserEntity::getStatus, UserStatus.PENDING)
                .set(UserEntity::getApplyReason, request.applyReason())
                .set(UserEntity::getRejectReason, null)
        );
    }
}

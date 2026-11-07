package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.controller.dto.MyPasswordResetDTO;
import com.pluchon.meng.user.controller.dto.PasswordResetDTO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.AuthorizationService;
import com.pluchon.meng.user.service.security.ContactService;
import com.pluchon.meng.user.service.security.PasswordService;
import com.pluchon.meng.user.service.security.RateLimitService;
import com.pluchon.meng.user.service.security.SessionService;
import com.pluchon.meng.user.service.security.VerificationCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 凭联系方式和验证码重置密码；登录后忘记原密码时凭绑定手机号的验证码重置
@Service
public class PasswordResetService {

    private static final String RATE_SCOPE = "password-reset";

    @Autowired
    private ContactService contacts;

    @Autowired
    private VerificationCodeService codes;

    @Autowired
    private CaptchaService captcha;

    @Autowired
    private RateLimitService limits;

    @Autowired
    private UserProperties properties;

    @Autowired
    private UserMapper mapper;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private SessionService sessions;

    @Autowired
    private AuthorizationService authorization;

    @Autowired
    private CodeDeliveryService delivery;

    // 验证码错误、账号不存在、管理员账号都返回同一个失败，不透露账号是否存在。
    // 成功后所有设备下线并清掉登录失败计数，审核状态不变
    @Transactional(rollbackFor = Exception.class)
    public void reset(PasswordResetDTO request, String ip) {
        ContactService.Contact contact = contacts.normalize(request.email(), request.countryCode(), request.phone());
        captcha.consume(request.ticket(), ip);
        var verification = properties.verification();
        limits.charge(RATE_SCOPE, contact.hash(), verification.maxErrors(), verification.codeTtl());
        String hash = passwords.hash(request.password(), request.confirmPassword());
        try {
            codes.consume(contact, request.code(), VerificationPurpose.PASSWORD_RESET);
        } catch (BusinessException exception) {
            throw new BusinessException(UserErrorCode.RESET_FAILED);
        }
        UserEntity user = contact.channel() == ContactChannel.EMAIL
            ? mapper.lockByEmailHash(contact.hash())
            : mapper.lockByPhoneHash(contact.hash());
        if (user == null || user.getRole() != UserRole.USER) {
            throw new BusinessException(UserErrorCode.RESET_FAILED);
        }
        user.setPasswordHash(hash);
        mapper.updateById(user);
        sessions.revokeAll(user.getId());
        limits.clearLogin(user.getUsername());
    }

    // 登录后忘记原密码：给绑定的手机号发码，返回模拟发码时的验证码
    public String sendMine(String ticket, String ip) {
        UserEntity user = authorization.requireActive(mapper.selectById(UserContext.require().userId()));
        return delivery.myResetCode(boundPhone(user), ticket, ip);
    }

    // 登录后凭手机验证码重置密码，成功后所有设备下线
    @Transactional(rollbackFor = Exception.class)
    public void resetMine(MyPasswordResetDTO request) {
        UserEntity user = authorization.requireActive(mapper.lockById(UserContext.require().userId()));
        ContactService.Contact phone = boundPhone(user);
        var verification = properties.verification();
        limits.charge(RATE_SCOPE, phone.hash(), verification.maxErrors(), verification.codeTtl());
        String hash = passwords.hash(request.password(), request.confirmPassword());
        try {
            codes.consume(phone, request.code(), VerificationPurpose.PASSWORD_RESET);
        } catch (BusinessException exception) {
            throw new BusinessException(UserErrorCode.RESET_FAILED);
        }
        user.setPasswordHash(hash);
        mapper.updateById(user);
        sessions.revokeAll(user.getId());
        limits.clearLogin(user.getUsername());
    }

    private ContactService.Contact boundPhone(UserEntity user) {
        if (user.getPhoneCipher() == null) {
            throw new BusinessException(UserErrorCode.PHONE_NOT_BOUND);
        }
        return new ContactService.Contact(ContactChannel.SMS, user.getPhoneHash(), user.getPhoneCipher());
    }
}

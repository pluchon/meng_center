package com.pluchon.meng.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.controller.dto.ContactChangeDTO;
import com.pluchon.meng.user.controller.dto.ProfileDTO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import com.pluchon.meng.user.entity.enums.VerificationPurpose;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    @Autowired
    private UserMapper users;

    @Autowired
    private AuthorizationService authorization;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private RateLimitService limits;

    @Autowired
    private ContactService contacts;

    @Autowired
    private VerificationCodeService codes;

    @Autowired
    private Clock clock;

    @Autowired
    private ContactChangeNotification notifications;

    @Transactional(rollbackFor = Exception.class)
    public void update(ProfileDTO dto) {
        UserEntity user = authorization.requireActive(
            users.lockById(UserContext.require().userId())
        );
        String nickname = dto.nickname().strip(),
            bio = dto.bio().strip(),
            name = UsernamePolicy.normalize(dto.username());
        if (
            (!nickname.isEmpty() &&
                (nickname.codePointCount(0, nickname.length()) < 2 ||
                    nickname.codePointCount(0, nickname.length()) > 16)) ||
            dto.bio().contains("\n") ||
            dto.bio().contains("\r")
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        LocalDateTime now = LocalDateTime.ofInstant(
            clock.instant(),
            ZoneId.of("Asia/Taipei")
        );
        if (!name.equals(user.getUsername())) {
            if (
                user.getUsernameUpdateTime() != null &&
                user.getUsernameUpdateTime().plusDays(30).isAfter(now)
            ) throw new BusinessException(
                UserErrorCode.USERNAME_COOLDOWN,
                java.util.Map.of(
                    "availableAt",
                    user.getUsernameUpdateTime().plusDays(30)
                )
            );
            user.setUsername(name);
            user.setUsernameUpdateTime(now);
        }
        user.setNickname(nickname);
        user.setBio(bio);
        try {
            users.updateById(user);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(UserErrorCode.USERNAME_TAKEN);
        }
    }

    public void requirePassword(UserEntity user, String password) {
        if (limits.loginLocked(user.getUsername())) throw new BusinessException(
            CommonErrorCode.RATE_LIMITED
        );
        if (!passwords.matches(password, user.getPasswordHash())) {
            limits.failedLogin(user.getUsername());
            throw new BusinessException(UserErrorCode.PASSWORD_WRONG);
        }
        limits.clearLogin(user.getUsername());
    }

    @Transactional(rollbackFor = Exception.class)
    public void contact(ContactChangeDTO dto, boolean email) {
        UserEntity user = authorization.requireActive(
            users.lockById(UserContext.require().userId())
        );
        requirePassword(user, dto.password());
        var contact = contacts.normalize(
            email ? dto.email() : null,
            email ? null : dto.countryCode(),
            email ? null : dto.phone()
        );
        codes.consume(contact, dto.code(), VerificationPurpose.CONTACT_CHANGE);
        var query = Wrappers.<UserEntity>lambdaQuery()
            .ne(UserEntity::getId, user.getId())
            .eq(email, UserEntity::getEmailHash, contact.hash())
            .eq(!email, UserEntity::getPhoneHash, contact.hash());
        if (users.exists(query)) throw new BusinessException(
            UserErrorCode.CONTACT_TAKEN
        );
        String old = email ? user.getEmailCipher() : null;
        String oldHash = email ? user.getEmailHash() : null;
        if (email) {
            user.setEmailCipher(contact.cipher());
            user.setEmailHash(contact.hash());
        } else {
            user.setPhoneCipher(contact.cipher());
            user.setPhoneHash(contact.hash());
        }
        try {
            users.updateById(user);
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(UserErrorCode.CONTACT_TAKEN);
        }
        if (
            old != null && !contact.hash().equals(oldHash)
        ) notifications.changed(old);
    }
}

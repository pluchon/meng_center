package com.pluchon.meng.user.converter;

import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.service.security.ContactService;
import org.springframework.stereotype.Component;

@Component
public final class UserConverter {

    private final ContactService contacts;

    public UserConverter(ContactService contacts) {
        this.contacts = contacts;
    }

    public UserVO toVO(UserEntity user) {
        return new UserVO(
            user.getId(),
            user.getUsername(),
            user.getNickname(),
            user.getAvatar(),
            user.getRole(),
            user.getStatus(),
            contacts.maskEmail(user.getEmailCipher()),
            contacts.maskPhone(user.getPhoneCipher()),
            user.getApplyReason(),
            user.getRejectReason(),
            user.getLevel(),
            user.getCreateTime()
        );
    }

    public PublicUserVO toPublicVO(UserEntity user) {
        return new PublicUserVO(user.getId(), user.getNickname(), user.getAvatar(), user.getLevel());
    }
}

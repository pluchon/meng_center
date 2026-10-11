package com.pluchon.meng.user.converter;

import cn.hutool.core.bean.BeanUtil;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.user.controller.vo.UserVO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.service.security.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 账号实体到对外视图的转换
@Component
public final class UserConverter {

    @Autowired
    private ContactService contacts;

    // 同名字段直接复制；邮箱和手机号在实体里是密文，这里解密后脱敏
    public UserVO toVO(UserEntity user) {
        UserVO view = BeanUtil.copyProperties(user, UserVO.class);
        view.setEmail(contacts.maskEmail(user.getEmailCipher()));
        view.setPhone(contacts.maskPhone(user.getPhoneCipher()));
        return view;
    }

    // 给其他服务展示用的公开资料，契约是不可变记录，只能走构造器
    public PublicUserVO toPublicVO(UserEntity user) {
        return new PublicUserVO(
            user.getId(),
            user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername()
                : user.getNickname(),
            user.getAvatar(),
            user.getLevel()
        );
    }
}

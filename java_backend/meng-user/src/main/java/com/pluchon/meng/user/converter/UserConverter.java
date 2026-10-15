package com.pluchon.meng.user.converter;

import cn.hutool.core.bean.BeanUtil;
import com.pluchon.meng.api.user.vo.PublicUserVO;
import com.pluchon.meng.user.controller.vo.FollowUserVO;
import com.pluchon.meng.user.controller.vo.UserProfileVO;
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
        view.setAvatar(avatarUrl(user.getAvatar()));
        view.setUsernameAvailableAt(
            user.getUsernameUpdateTime() == null
                ? null
                : user.getUsernameUpdateTime().plusDays(30)
        );
        return view;
    }

    // 别人的主页资料：昵称没填时用用户名，头像换成可访问的地址
    public UserProfileVO toProfileVO(UserEntity user) {
        UserProfileVO view = BeanUtil.copyProperties(user, UserProfileVO.class);
        view.setNickname(displayName(user));
        view.setAvatar(avatarUrl(user.getAvatar()));
        return view;
    }

    // 关注和粉丝列表里的一个人
    public FollowUserVO toFollowUserVO(UserEntity user) {
        FollowUserVO view = BeanUtil.copyProperties(user, FollowUserVO.class);
        view.setNickname(displayName(user));
        view.setAvatar(avatarUrl(user.getAvatar()));
        return view;
    }

    private String displayName(UserEntity user) {
        return user.getNickname() == null || user.getNickname().isBlank()
            ? user.getUsername()
            : user.getNickname();
    }

    // 给其他服务展示用的公开资料，契约是不可变记录，只能走构造器
    public PublicUserVO toPublicVO(UserEntity user) {
        return new PublicUserVO(
            user.getId(),
            user.getNickname() == null || user.getNickname().isBlank()
                ? user.getUsername()
                : user.getNickname(),
            avatarUrl(user.getAvatar()),
            user.getLevel()
        );
    }

    private String avatarUrl(String value) {
        return value == null || !value.matches("[a-f0-9-]{36}\\.webp")
            ? value
            : "/user/avatars/" + value;
    }
}

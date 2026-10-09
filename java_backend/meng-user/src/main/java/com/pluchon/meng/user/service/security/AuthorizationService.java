package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 身份与权限的最终判定：以数据库里的账号状态和角色为准，不信任请求头里的角色
@Service
public final class AuthorizationService {

    @Autowired
    private UserMapper mapper;

    // 账号不存在或不是正常状态都按未登录处理
    public UserEntity requireActive(UserEntity user) {
        if (user == null || user.getStatus() != UserStatus.NORMAL) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        return user;
    }

    // 当前登录用户
    public UserEntity current() {
        return requireActive(mapper.selectById(UserContext.require().userId()));
    }

    // 当前登录用户必须是管理员
    public void requireAdmin() {
        if (current().getRole() != UserRole.ADMIN) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
    }
}

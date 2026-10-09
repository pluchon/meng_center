package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.*;
import com.pluchon.meng.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public final class AuthorizationService {

    private final UserMapper mapper;

    public AuthorizationService(UserMapper mapper) {
        this.mapper = mapper;
    }

    public UserEntity current() {
        var identity = UserContext.require();
        var user = mapper.selectById(identity.userId());
        if (user == null || user.getStatus() != UserStatus.NORMAL) throw new BusinessException(
            CommonErrorCode.UNAUTHENTICATED
        );
        return user;
    }

    public void requireAdmin() {
        var user = current();
        if (user.getRole() != UserRole.ADMIN) throw new BusinessException(CommonErrorCode.FORBIDDEN);
    }
}

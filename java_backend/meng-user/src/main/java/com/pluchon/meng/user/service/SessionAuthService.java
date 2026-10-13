package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.web.context.RequestIdentity;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.controller.dto.LoginDTO;
import com.pluchon.meng.user.controller.dto.PasswordChangeDTO;
import com.pluchon.meng.user.controller.vo.RejectionVO;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.AuthorizationService;
import com.pluchon.meng.user.service.security.PasswordService;
import com.pluchon.meng.user.service.security.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 登录、退出和改密
@Service
public class SessionAuthService {

    @Autowired
    private UserMapper mapper;

    @Autowired
    private CredentialService credentials;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private SessionService sessions;

    @Autowired
    private AuthorizationService authorization;

    @Autowired
    private ProfileService profiles;

    // 密码正确之后才透露审核状态，否则一律是账号或密码错误
    @Transactional(rollbackFor = Exception.class)
    public String login(LoginDTO request, String ip) {
        return login(request, ip, "");
    }

    @Transactional(rollbackFor = Exception.class)
    public String login(LoginDTO request, String ip, String agent) {
        UserEntity user = credentials.authenticate(
            request.username(),
            request.password(),
            request.ticket(),
            ip
        );
        if (user.getStatus() == UserStatus.PENDING) {
            throw new BusinessException(UserErrorCode.PENDING);
        }
        if (user.getStatus() == UserStatus.REJECTED) {
            throw new BusinessException(
                UserErrorCode.REJECTED,
                new RejectionVO(user.getRejectReason())
            );
        }
        return sessions.create(user, ip, agent);
    }

    // 只退出当前设备
    public void logout() {
        RequestIdentity identity = UserContext.require();
        sessions.revoke(identity.userId(), identity.sessionId());
    }

    // 改密成功后所有设备下线，包括当前这台
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(PasswordChangeDTO request) {
        UserEntity user = authorization.requireActive(
            mapper.lockById(UserContext.require().userId())
        );
        profiles.requirePassword(user, request.oldPassword());
        user.setPasswordHash(
            passwords.hash(request.password(), request.confirmPassword())
        );
        mapper.updateById(user);
        sessions.revokeAll(user.getId());
    }
}

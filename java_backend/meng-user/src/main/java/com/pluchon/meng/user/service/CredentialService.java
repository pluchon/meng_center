package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.error.UserErrorCode;
import com.pluchon.meng.user.service.security.PasswordService;
import com.pluchon.meng.user.service.security.RateLimitService;
import com.pluchon.meng.user.service.security.UsernamePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

// 用户名加密码的校验，登录和重新申请共用同一套失败计数与锁定
@Service
public class CredentialService {

    @Autowired
    private UserMapper mapper;

    @Autowired
    private PasswordService passwords;

    @Autowired
    private RateLimitService limits;

    @Autowired
    private CaptchaService captcha;

    // 依次检查锁定、滑块票据和密码，返回已加行锁的账号。必须在调用方的事务里使用：
    // 行锁让登录与改密、重置密码互斥，不会给旧密码签出新会话
    public UserEntity authenticate(String username, String password, String ticket, String ip) {
        String name = UsernamePolicy.normalize(username);
        if (limits.loginLocked(name)) {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        }
        captcha.consume(ticket, ip);
        UserEntity user = mapper.lockByUsername(name);
        if (!passwords.matches(password, user == null ? null : user.getPasswordHash())) {
            limits.failedLogin(name);
            throw new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
        }
        limits.clearLogin(name);
        return user;
    }
}

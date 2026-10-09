package com.pluchon.meng.user.service;

import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.security.PasswordService;
import com.pluchon.meng.user.service.security.UsernamePolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 管理员账号的一次性初始化，由独立的启动参数触发
@Service
public class AdminInitializationService {

    @Autowired
    private UserMapper mapper;

    @Autowired
    private PasswordService passwords;

    // 账号已存在就原样保留，不重设密码；同名的普通账号不会被提升为管理员
    @Transactional(rollbackFor = Exception.class)
    public void initialize(String username, String password) {
        if (!UsernamePolicy.valid(username)) {
            throw new IllegalArgumentException("Administrator username is invalid.");
        }
        UserEntity existing = mapper.lockByUsername(username);
        if (existing != null) {
            if (existing.getRole() != UserRole.ADMIN) {
                throw new IllegalStateException("The requested account already exists as a non-administrator.");
            }
            return;
        }
        UserEntity administrator = new UserEntity();
        administrator.setUsername(username);
        administrator.setNickname(username);
        administrator.setPasswordHash(passwords.hash(password, password));
        administrator.setRole(UserRole.ADMIN);
        administrator.setStatus(UserStatus.NORMAL);
        mapper.insert(administrator);
    }
}

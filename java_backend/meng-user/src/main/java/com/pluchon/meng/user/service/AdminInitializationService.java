package com.pluchon.meng.user.service;

import com.pluchon.meng.user.entity.UserEntity;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import com.pluchon.meng.user.mapper.UserMapper;
import com.pluchon.meng.user.service.security.PasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInitializationService {

    private final UserMapper mapper;
    private final PasswordService passwords;

    public AdminInitializationService(UserMapper mapper, PasswordService passwords) {
        this.mapper = mapper;
        this.passwords = passwords;
    }

    @Transactional
    public void initialize(String username, String password) {
        if (username == null || !username.matches("[a-z0-9_]{3,32}")) throw new IllegalArgumentException(
            "Administrator username is invalid."
        );
        UserEntity existing = mapper.lockByUsername(username);
        if (existing != null) {
            if (existing.getRole() != UserRole.ADMIN) throw new IllegalStateException(
                "The requested account already exists as a non-administrator."
            );
            return;
        }
        UserEntity administrator = new UserEntity();
        administrator.setUsername(username);
        administrator.setNickname(username);
        administrator.setPasswordHash(passwords.hash(password, password));
        administrator.setRole(UserRole.ADMIN);
        administrator.setStatus(UserStatus.NORMAL);
        administrator.setLevel(0);
        administrator.setDeleteState(0);
        mapper.insert(administrator);
    }
}

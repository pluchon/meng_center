package com.pluchon.meng.user.service;

import com.pluchon.meng.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserLevelService {

    private final UserMapper users;

    public void update(long user, int level) {
        if (user <= 0 || level < 1) throw new IllegalArgumentException(
            "Invalid level event"
        );
        users.raiseLevel(user, level);
    }
}

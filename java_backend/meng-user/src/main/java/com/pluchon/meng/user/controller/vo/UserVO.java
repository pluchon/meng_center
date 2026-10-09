package com.pluchon.meng.user.controller.vo;

import com.pluchon.meng.user.entity.enums.*;
import java.time.LocalDateTime;

public record UserVO(
    long id,
    String username,
    String nickname,
    String avatar,
    UserRole role,
    UserStatus status,
    String email,
    String phone,
    String applyReason,
    String rejectReason,
    int level,
    LocalDateTime createTime
) {}

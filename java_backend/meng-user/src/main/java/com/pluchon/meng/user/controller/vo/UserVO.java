package com.pluchon.meng.user.controller.vo;

import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import java.time.LocalDateTime;
import lombok.Data;

// 账号信息，邮箱和手机号只给脱敏后的值
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String bio;
    private LocalDateTime usernameAvailableAt;
    private UserRole role;
    private UserStatus status;
    // 脱敏邮箱，只露首字符和域名
    private String email;
    // 脱敏手机号，只露区号和末三位
    private String phone;
    private String applyReason;
    private String rejectReason;
    private Integer level;
    private Integer followingCount;
    private Integer followerCount;
    private LocalDateTime createTime;
}

package com.pluchon.meng.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.pluchon.meng.user.entity.enums.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String emailCipher;
    private String emailHash;
    private String phoneCipher;
    private String phoneHash;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private UserRole role;
    private UserStatus status;
    private String applyReason;
    private String rejectReason;
    private String termsVersion;
    private String privacyVersion;
    private LocalDateTime agreedAt;
    private Integer level;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleteState;
}

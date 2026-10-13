package com.pluchon.meng.user.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pluchon.meng.user.entity.enums.UserRole;
import com.pluchon.meng.user.entity.enums.UserStatus;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 账号表。等级和删除标记的初始值由数据库默认值给出，新建时不要在代码里赋值
@Getter
@Setter
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    // 邮箱和手机号各存一份密文和一份检索哈希，二者最多填一种
    private String emailCipher;
    private String emailHash;
    private String phoneCipher;
    private String phoneHash;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private String bio;
    private LocalDateTime usernameUpdateTime;
    private UserRole role;
    private UserStatus status;
    private String applyReason;
    private String rejectReason;
    // 注册时同意的协议版本和时间
    private String termsVersion;
    private String privacyVersion;
    private LocalDateTime agreedAt;
    private Integer level;

    // 创建和更新时间由数据库维护，写入时不带这两列，否则会把更新时间钉在旧值上
    @TableField(
        insertStrategy = FieldStrategy.NEVER,
        updateStrategy = FieldStrategy.NEVER
    )
    private LocalDateTime createTime;

    @TableField(
        insertStrategy = FieldStrategy.NEVER,
        updateStrategy = FieldStrategy.NEVER
    )
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleteState;
}

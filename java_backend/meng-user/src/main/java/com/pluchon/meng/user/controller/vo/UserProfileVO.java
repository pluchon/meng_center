package com.pluchon.meng.user.controller.vo;

import java.time.LocalDateTime;
import lombok.Data;

// 别人的公开主页资料：不含用户名、联系方式和账号状态
@Data
public class UserProfileVO {

    private Long id;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer level;
    // 来到部落的时间
    private LocalDateTime createTime;
    private Integer followingCount;
    private Integer followerCount;
    // 当前用户是否关注了他
    private boolean followed;
    // 他是否关注了当前用户
    private boolean followsMe;
    // 是不是当前用户自己
    private boolean self;
}

package com.pluchon.meng.user.controller.vo;

import java.time.LocalDateTime;
import lombok.Data;

// 关注或粉丝列表里的一个人
@Data
public class FollowUserVO {

    private Long id;
    private String nickname;
    private String avatar;
    private String bio;
    private Integer level;
    // 关注或被关注的时间
    private LocalDateTime followTime;
    // 当前用户是否关注了他
    private boolean followed;
    // 他是否关注了当前用户
    private boolean followsMe;
    // 是不是当前用户自己
    private boolean self;
}

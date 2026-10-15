package com.pluchon.meng.api.user.event;

// 有人关注了另一个人，关注事务提交后发出；取消关注不发
public record UserFollowedEvent(long followerId, long followeeId) {
    public static final String EVENT_TYPE = "user.followed";
    public static final String EXCHANGE = "meng.user.topic";
}

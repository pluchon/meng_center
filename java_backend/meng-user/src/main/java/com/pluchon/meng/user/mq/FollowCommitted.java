package com.pluchon.meng.user.mq;

// 关注已写库的进程内事件，事务提交后才会被处理
public record FollowCommitted(long followerId, long followeeId) {}

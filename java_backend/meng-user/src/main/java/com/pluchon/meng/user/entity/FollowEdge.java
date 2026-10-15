package com.pluchon.meng.user.entity;

import java.time.LocalDateTime;

// 关注列表里的一条：对方是谁、什么时候关注的
public record FollowEdge(Long userId, LocalDateTime createTime) {}

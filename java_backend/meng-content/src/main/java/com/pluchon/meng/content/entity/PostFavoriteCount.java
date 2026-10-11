package com.pluchon.meng.content.entity;

// 帖子收藏数量
public record PostFavoriteCount(
        // 所属帖子编号
        long postId,
        // 收藏数量
        long count) {}

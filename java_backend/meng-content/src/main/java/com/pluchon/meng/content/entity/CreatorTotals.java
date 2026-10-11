package com.pluchon.meng.content.entity;

// 当前未删除帖子的聚合结果
public record CreatorTotals(
        // 帖子数
        long postCount,
        // 浏览数
        long viewCount,
        // 喜欢数
        long likeCount,
        // 评论数
        long commentCount) {}

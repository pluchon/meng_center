package com.pluchon.meng.content.controller.vo;

// 作者自己的累计数据
public record CreatorOverviewVO(
        // 帖子数
        long postCount,
        // 浏览数
        long viewCount,
        // 喜欢数
        long likeCount,
        // 评论数
        long commentCount,
        // 收藏数
        long favoriteCount) {}

package com.pluchon.meng.content.controller.vo;

import java.time.LocalDate;

// 连续日期的每日新增数据
public record CreatorTrendVO(
        // 业务日期
        LocalDate date,
        // 浏览数
        long viewCount,
        // 喜欢数
        long likeCount,
        // 评论数
        long commentCount,
        // 收藏数
        long favoriteCount,
        // 帖子数
        long postCount) {}

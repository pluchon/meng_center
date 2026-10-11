package com.pluchon.meng.content.controller.vo;

// 作者帖子表现排行
public record CreatorTopPostVO(
        // 帖子编号
        long id,
        // 标题
        String title,
        // 热度分
        long hotScore) {}

package com.pluchon.meng.content.entity;

// 帖子关联标签名称
public record PostTagName(
        // 所属帖子编号
        long postId,
        // 标签显示名称
        String name) {}

package com.pluchon.meng.content.entity;

import java.util.List;

// 可重建的帖子搜索文档
public record SearchDocument(
        // 记录主键
        long id,
        // 标题
        String title,
        // 正文
        String content,
        // 标签名称集合
        List<String> tags,
        // 帖子分类
        String category,
        // 创建时间
        String createTime) {}

package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.content.entity.enums.PostCategory;

import java.util.List;

// 草稿编辑数据
public record DraftVO(
        // 记录主键
        long id,
        // 帖子分类
        PostCategory category,
        // 标题
        String title,
        // 正文
        String content,
        // 配图展示集合
        List<ImageVO> images,
        // 封面配图展示数据
        ImageVO cover,
        // 标签名称集合
        List<String> tags) {}

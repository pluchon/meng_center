package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;

// 作者帖子列表，封面使用缩略图
public record CreatorPostVO(
        // 帖子编号
        long id,
        // 帖子分类
        PostCategory category,
        // 标题
        String title,
        // 摘要
        String summary,
        // 封面缩略图
        ImageVO cover,
        // 发布时间
        LocalDateTime createTime,
        // 浏览数
        long viewCount,
        // 喜欢数
        long likeCount,
        // 评论数
        long commentCount,
        // 收藏数
        long favoriteCount) {}

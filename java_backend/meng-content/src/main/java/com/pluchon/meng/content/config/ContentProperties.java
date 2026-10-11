package com.pluchon.meng.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 内容操作配额、热度和存储配置
@ConfigurationProperties("meng.content")
public record ContentProperties(
        // 图片存储目录
        String imageRoot,
        // 点赞热度权重
        int likeWeight,
        // 发帖最小间隔
        Duration postInterval,
        // 每日发帖上限
        int postsDaily,
        // 每日帖子配图上传上限
        int uploadsDaily,
        // 业务日期时区
        String timezone,
        // 草稿保存最小间隔
        @org.springframework.boot.context.properties.bind.DefaultValue("3s") Duration draftInterval,
        // 浏览热度权重
        @org.springframework.boot.context.properties.bind.DefaultValue("1") int viewWeight,
        // 评论热度权重
        @org.springframework.boot.context.properties.bind.DefaultValue("3") int commentWeight,
        // 评论最小间隔
        @org.springframework.boot.context.properties.bind.DefaultValue("10s")
                Duration commentInterval,
        // 举报最小间隔
        @org.springframework.boot.context.properties.bind.DefaultValue("10s")
                Duration reportInterval,
        // 每日举报上限
        @org.springframework.boot.context.properties.bind.DefaultValue("30") int reportsDaily,
        // 每日评论配图上传上限
        @org.springframework.boot.context.properties.bind.DefaultValue("100")
                int commentUploadsDaily,
        // 收藏热度权重
        @org.springframework.boot.context.properties.bind.DefaultValue("5") int favoriteWeight) {}

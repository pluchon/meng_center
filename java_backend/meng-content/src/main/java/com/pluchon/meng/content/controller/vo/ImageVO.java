package com.pluchon.meng.content.controller.vo;

// 配图展示数据
public record ImageVO(
        // 记录主键
        long id,
        // 原图资源地址
        String url,
        // 缩略图资源地址
        String thumbUrl,
        // 原图宽度
        int width,
        // 原图高度
        int height) {}

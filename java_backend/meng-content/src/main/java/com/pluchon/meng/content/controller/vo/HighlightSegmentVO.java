package com.pluchon.meng.content.controller.vo;

// 高亮文本片段
public record HighlightSegmentVO(
        // 展示文本
        String text,
        // 是否匹配搜索词
        boolean matched) {}

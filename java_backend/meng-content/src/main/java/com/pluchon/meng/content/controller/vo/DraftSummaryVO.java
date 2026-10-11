package com.pluchon.meng.content.controller.vo;

import com.pluchon.meng.content.entity.enums.PostCategory;
import java.time.LocalDateTime;
import java.util.List;

// 草稿摘要数据
public record DraftSummaryVO(
    // 记录主键
    long id,
    // 可空的草稿分类
    PostCategory category,
    // 标题
    String title,
    // 正文摘要
    String summary,
    // 前五张配图
    List<ImageVO> images,
    // 配图总数
    int imageCount,
    // 更新时间
    LocalDateTime updateTime
) {}

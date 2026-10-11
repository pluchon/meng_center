package com.pluchon.meng.content.entity;

import java.time.LocalDate;

// 按作者和台北日期聚合的发帖数量
public record AuthorPublicationCount(
        // 业务日期
        LocalDate date,
        // 发布数量
        long count) {}

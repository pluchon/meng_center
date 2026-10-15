package com.pluchon.meng.growth.entity;

import com.pluchon.meng.growth.entity.enums.PointSource;

// 日期范围内单个来源获得的积分聚合
public record SourcePoints(
        // 积分来源
        PointSource source,
        // 区间内该来源获得的积分合计
        long amount) {}

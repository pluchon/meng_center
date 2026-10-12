package com.pluchon.meng.growth.entity;

import com.pluchon.meng.growth.entity.enums.ExpSource;

// 按来源聚合的今日次数、经验及累计活跃日期
public record SourceExperience(
        // 奖励来源
        ExpSource source,
        // 区间内奖励次数
        long count,
        // 区间内经验合计
        long earned,
        // 获得奖励的不同日期数
        long activeDays) {}

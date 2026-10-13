package com.pluchon.meng.growth.entity;

import java.time.LocalDate;

// 日期范围内单日经验聚合
public record DailyExperience(
        // 业务日期
        LocalDate date,
        // 当日经验合计
        long amount) {}

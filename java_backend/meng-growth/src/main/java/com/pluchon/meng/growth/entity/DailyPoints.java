package com.pluchon.meng.growth.entity;

import java.time.LocalDate;

// 日期范围内单日获得的积分聚合
public record DailyPoints(
        // 业务日期
        LocalDate date,
        // 当日获得的积分合计
        long amount) {}

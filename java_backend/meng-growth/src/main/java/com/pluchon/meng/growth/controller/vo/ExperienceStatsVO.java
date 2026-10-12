package com.pluchon.meng.growth.controller.vo;

import com.pluchon.meng.growth.entity.enums.ExpSource;
import java.time.LocalDate;
import java.util.List;

// 连续日期补零后的经验趋势与同一区间来源合计
public record ExperienceStatsVO(
        // 连续每日经验，按日期升序
        List<Day> days,
        // 三个来源的区间经验
        List<Source> sources) {
    // 单日经验合计
    public record Day(
            // 业务日期
            LocalDate date,
            // 当日经验合计
            long amount) {}

    // 单个来源的经验合计
    public record Source(
            // 奖励来源
            ExpSource source,
            // 所选区间的经验合计
            long amount) {}
}

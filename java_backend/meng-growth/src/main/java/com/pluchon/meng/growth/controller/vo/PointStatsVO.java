package com.pluchon.meng.growth.controller.vo;

import com.pluchon.meng.growth.entity.enums.PointSource;
import java.time.LocalDate;
import java.util.List;

// 连续日期补零后的积分趋势与同一区间来源合计
public record PointStatsVO(
        // 连续每日获得的积分，按日期升序
        List<Day> days,
        // 每个来源的区间合计
        List<Source> sources) {
    // 单日获得的积分合计
    public record Day(
            // 业务日期
            LocalDate date,
            // 当日获得的积分合计
            long amount) {}

    // 单个来源的积分合计
    public record Source(
            // 积分来源
            PointSource source,
            // 所选区间获得的积分合计
            long amount) {}
}

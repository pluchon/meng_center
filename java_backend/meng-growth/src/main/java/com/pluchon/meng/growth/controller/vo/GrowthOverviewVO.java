package com.pluchon.meng.growth.controller.vo;

import com.pluchon.meng.growth.entity.enums.ExpSource;
import java.time.LocalDate;
import java.util.List;

// 配置等级之路和今日任务的独立查询结果
public record GrowthOverviewVO(
        // 业务时区的当前日期
        LocalDate today,
        // 业务时区，供前端显示经验记录时间
        String timezone,
        // 配置中的全部等级门槛
        List<Level> levels,
        // 三项任务的今日进度和累计天数
        List<Task> tasks) {
    // 单个等级的累计经验门槛
    public record Level(
            // 等级序号
            int level,
            // 达到该等级的累计经验
            long requiredExp) {}

    // 单个来源的奖励配置与实际统计
    public record Task(
            // 奖励来源
            ExpSource source,
            // 单次奖励经验
            int reward,
            // 每日最多奖励次数
            int dailyMax,
            // 今日已奖励次数
            long count,
            // 今日已获得经验
            long earned,
            // 曾获得该来源经验的日期数
            long activeDays) {}
}

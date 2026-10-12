package com.pluchon.meng.growth.controller.vo;

import java.time.LocalDate;
import java.util.List;

// 所选月份的签到日期和截至当前日期的连续签到
public record CheckInMonthVO(
        // 所选月份
        String month,
        // 当前业务日期
        LocalDate today,
        // 所选月份已签的日期
        List<LocalDate> dates,
        // 所选月份签到天数
        int count,
        // 截至今天或昨天的连续天数
        long streak,
        // 当前配置中的单次签到经验
        int reward) {}

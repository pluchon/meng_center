package com.pluchon.meng.growth.controller.vo;

import java.time.LocalDate;
import java.util.List;

// 成长概况与本周签到数据
public record GrowthVO(
        // 当前等级
        int level,
        // 累计经验
        long exp,
        // 当前等级起始经验
        long levelStartExp,
        // 下一级所需累计经验
        Long nextLevelExp,
        // 今天是否已签到
        boolean checkedIn,
        // 业务时区的当前日期
        LocalDate today,
        // 本周签到状态
        List<Day> week) {

    public record Day(
            // 签到日期
            LocalDate date,
            // 今天是否已签到
            boolean checkedIn) {}
}

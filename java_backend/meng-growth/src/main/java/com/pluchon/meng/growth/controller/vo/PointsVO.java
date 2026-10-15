package com.pluchon.meng.growth.controller.vo;

import com.pluchon.meng.growth.entity.enums.PointSource;
import java.util.List;

// 积分余额和各来源的获得规则
public record PointsVO(
        // 当前余额
        long balance,
        // 每种来源单次得多少、每天最多算几次
        List<Rule> rules) {

    // 一条获得规则：单次得多少积分，每天最多算几次
    public record Rule(PointSource source, int amount, int dailyMax) {}
}

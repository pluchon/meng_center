package com.pluchon.meng.game.controller.vo;

import com.pluchon.meng.game.entity.enums.SpotKind;

// 拾取结果：这次得到多少，已经拿过时 taken 为真、金额为 0
public record PickupVO(
        // 点位标识
        String spotId,
        // 点位种类
        SpotKind kind,
        // 这次得到的金币
        int amount,
        // 这个周期是否早已拿过
        boolean taken,
        // 最新金币余额
        long coins,
        // 已得到的星之碎片数
        int stars) {}

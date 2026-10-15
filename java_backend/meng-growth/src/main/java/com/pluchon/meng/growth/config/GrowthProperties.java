package com.pluchon.meng.growth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// 成长奖励、每日上限和等级门槛配置
@ConfigurationProperties("meng.growth")
public record GrowthProperties(
        // 业务日期时区
        String timezone,
        // 签到经验奖励
        int checkInExp,
        // 发帖经验奖励
        int postExp,
        // 评论经验奖励
        int commentExp,
        // 每日发帖奖励次数上限
        int postDailyMax,
        // 每日评论奖励次数上限
        int commentDailyMax,
        // 各等级累计经验门槛
        List<Long> thresholds,
        // 签到积分奖励
        int checkInPoints,
        // 发帖积分奖励
        int postPoints,
        // 评论积分奖励
        int commentPoints) {

    public GrowthProperties {
        if (thresholds == null || thresholds.isEmpty() || thresholds.get(0) != 0L)
            throw new IllegalArgumentException("Levels must start at zero");
        for (int i = 1; i < thresholds.size(); i++)
            if (thresholds.get(i) <= thresholds.get(i - 1))
                throw new IllegalArgumentException("Level thresholds must increase");
        if (checkInExp < 1
                || postExp < 1
                || commentExp < 1
                || postDailyMax < 1
                || commentDailyMax < 1
                || checkInPoints < 1
                || postPoints < 1
                || commentPoints < 1) throw new IllegalArgumentException("Invalid rewards");
        java.time.ZoneId.of(timezone);
        thresholds = List.copyOf(thresholds);
    }
}

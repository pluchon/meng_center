package com.pluchon.meng.growth.config;

import com.pluchon.meng.growth.entity.enums.ExpSource;
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
        // 点赞他人帖子经验奖励
        int likeExp,
        // 每日发帖奖励次数上限
        int postDailyMax,
        // 每日评论奖励次数上限
        int commentDailyMax,
        // 每日点赞奖励次数上限
        int likeDailyMax,
        // 各等级累计经验门槛
        List<Long> thresholds,
        // 签到积分奖励
        int checkInPoints,
        // 发帖积分奖励
        int postPoints,
        // 评论积分奖励
        int commentPoints,
        // 点赞积分奖励
        int likePoints) {

    public GrowthProperties {
        if (thresholds == null || thresholds.isEmpty() || thresholds.get(0) != 0L)
            throw new IllegalArgumentException("Levels must start at zero");
        for (int i = 1; i < thresholds.size(); i++)
            if (thresholds.get(i) <= thresholds.get(i - 1))
                throw new IllegalArgumentException("Level thresholds must increase");
        if (checkInExp < 1
                || postExp < 1
                || commentExp < 1
                || likeExp < 1
                || postDailyMax < 1
                || commentDailyMax < 1
                || likeDailyMax < 1
                || checkInPoints < 1
                || postPoints < 1
                || commentPoints < 1
                || likePoints < 1) throw new IllegalArgumentException("Invalid rewards");
        java.time.ZoneId.of(timezone);
        thresholds = List.copyOf(thresholds);
    }

    // 某个来源每次发放的经验
    public int exp(ExpSource source) {
        return switch (source) {
            case CHECK_IN -> checkInExp;
            case POST -> postExp;
            case COMMENT -> commentExp;
            case LIKE -> likeExp;
        };
    }

    // 某个来源每次发放的积分
    public int points(ExpSource source) {
        return switch (source) {
            case CHECK_IN -> checkInPoints;
            case POST -> postPoints;
            case COMMENT -> commentPoints;
            case LIKE -> likePoints;
        };
    }

    // 某个来源每天最多发放几次
    public int dailyMax(ExpSource source) {
        return switch (source) {
            case CHECK_IN -> 1;
            case POST -> postDailyMax;
            case COMMENT -> commentDailyMax;
            case LIKE -> likeDailyMax;
        };
    }
}

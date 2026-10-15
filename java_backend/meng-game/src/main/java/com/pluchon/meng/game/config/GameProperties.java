package com.pluchon.meng.game.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

// 拾取距离、宝箱金币、通关奖励和岛的范围
@ConfigurationProperties("meng.game")
public record GameProperties(
        // 业务日期时区，每日金币点位按它切天
        String timezone,
        // 拾取时玩家到点位的最大水平距离
        double pickupDistance,
        // 每个宝箱的金币
        int chestCoins,
        // 点亮神殿的一次性奖励金币
        int clearReward,
        // 存档位置允许的最大水平半径
        double radius,
        // 存档位置允许的最低高度
        double minY,
        // 存档位置允许的最高高度
        double maxY) {

    public GameProperties {
        ZoneId.of(timezone);
        if (pickupDistance <= 0
                || chestCoins < 1
                || clearReward < 1
                || radius <= 0
                || minY >= maxY) throw new IllegalArgumentException("Invalid game settings");
    }
}

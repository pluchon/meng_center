package com.pluchon.meng.growth.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meng.growth")
public record GrowthProperties(
    String timezone,
    int checkInExp,
    int postExp,
    int commentExp,
    int postDailyMax,
    int commentDailyMax,
    List<Long> thresholds
) {
    public GrowthProperties {
        if (
            thresholds == null ||
            thresholds.isEmpty() ||
            thresholds.get(0) != 0L
        ) throw new IllegalArgumentException("Levels must start at zero");
        for (int i = 1; i < thresholds.size(); i++) if (
            thresholds.get(i) <= thresholds.get(i - 1)
        ) throw new IllegalArgumentException("Level thresholds must increase");
        if (
            checkInExp < 1 ||
            postExp < 1 ||
            commentExp < 1 ||
            postDailyMax < 1 ||
            commentDailyMax < 1
        ) throw new IllegalArgumentException("Invalid rewards");
        java.time.ZoneId.of(timezone);
        thresholds = List.copyOf(thresholds);
    }
}

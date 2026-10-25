package com.pluchon.meng.companion.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 关系门槛、次数及危机卡片从 Nacos 获取，存储目录只从环境读取
@Data
@ConfigurationProperties(prefix = "meng.companion")
@Validated
public class CompanionProperties {

    private List<Integer> thresholds = List.of(0, 100, 300, 600, 1000);

    @Min(1000)
    private int affectionMax = 1200;

    private int dailyGain = 30;
    private int firstChatGain = 10;
    private int messageDaily = 200;
    private int messageMinute = 10;
    private int toyDaily = 5;
    private int toyMinute = 1;
    private int webDaily = 30;
    private int webMinute = 3;
    private int webImageDaily = 10;
    private int webImageMinute = 2;
    private int imageDaily = 3;
    private int imageMinute = 1;
    private int concurrentTurns = 2;
    private int ipMinute = 60;
    private java.util.Map<String,String> holidays = java.util.Map.of();
    private List<Hotline> hotlines = List.of();

    public record Hotline(
        String region,
        String name,
        String phone,
        String url
    ) {}

    @AssertTrue(message = "阶段门槛必须从零开始严格递增，所有次数必须为正数")
    public boolean isConfigurationValid() {
        if (
            thresholds == null ||
            thresholds.size() != 5 ||
            thresholds.get(0) != 0
        ) return false;
        for (int index = 1; index < thresholds.size(); index++) if (
            thresholds.get(index) <= thresholds.get(index - 1)
        ) return false;
        return (
            affectionMax >= thresholds.get(4) &&
            dailyGain >= 0 &&
            firstChatGain >= 0 &&
            messageDaily > 0 &&
            messageMinute > 0 &&
            toyDaily > 0 &&
            toyMinute > 0 &&
            imageDaily > 0 &&
            imageMinute > 0 &&
            concurrentTurns > 0 &&
            ipMinute > 0
        );
    }
}

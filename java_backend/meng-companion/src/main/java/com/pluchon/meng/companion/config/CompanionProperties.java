package com.pluchon.meng.companion.config;

import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 次数及危机卡片从 Nacos 获取，存储目录只从环境读取
@Data
@ConfigurationProperties(prefix = "meng.companion")
@Validated
public class CompanionProperties {

    private int messageDaily = 200;
    private int messageMinute = 10;
    private int concurrentTurns = 2;
    private int ipMinute = 60;
    private List<Hotline> hotlines = List.of();

    public record Hotline(
        String region,
        String name,
        String phone,
        String url
    ) {}

    @AssertTrue(message="次数与并发数必须为正数")
    public boolean isConfigurationValid() {
        return messageDaily > 0 && messageMinute > 0 && concurrentTurns > 0 && ipMinute > 0;
    }
}

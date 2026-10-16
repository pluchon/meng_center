package com.pluchon.meng.growth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 成长总结的用户配额
@ConfigurationProperties("meng.growth.ai")
public record GrowthAiProperties(@DefaultValue("5") int daily, @DefaultValue("2") int minute) {}

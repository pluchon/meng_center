package com.pluchon.meng.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 内容域 AI 辅助功能的用户配额
@ConfigurationProperties("meng.content.ai")
public record AiProperties(
    @DefaultValue("30") int tagsDaily,
    @DefaultValue("6") int tagsMinute,
    @DefaultValue("10") int inspirationsDaily,
    @DefaultValue("3") int inspirationsMinute,
    @DefaultValue("3") int coversDaily,
    @DefaultValue("1") int coversMinute,
    @DefaultValue("5") int reviewsDaily,
    @DefaultValue("2") int reviewsMinute,
    @DefaultValue("100") int feedbackDaily,
    @DefaultValue("20") int feedbackMinute
) {}

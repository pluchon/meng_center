package com.pluchon.meng.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

// 帖子搜索分词器与故障冷却配置
@ConfigurationProperties("meng.content.search")
public record SearchProperties(
        // 索引分词器
        @DefaultValue("ik_max_word") String analyzer,
        // 检索分词器
        @DefaultValue("ik_smart") String searchAnalyzer,
        // 搜索失败后的冷却时长
        @DefaultValue("10s") Duration failureCooldown) {}

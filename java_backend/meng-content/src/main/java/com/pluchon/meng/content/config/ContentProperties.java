package com.pluchon.meng.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("meng.content")
public record ContentProperties(
        String imageRoot,
        int likeWeight,
        Duration postInterval,
        int postsDaily,
        int uploadsDaily,
        String timezone,
        @org.springframework.boot.context.properties.bind.DefaultValue("3s") Duration draftInterval,
        @org.springframework.boot.context.properties.bind.DefaultValue("1") int viewWeight,
        @org.springframework.boot.context.properties.bind.DefaultValue("3") int commentWeight,
        @org.springframework.boot.context.properties.bind.DefaultValue("10s")
                Duration commentInterval,
        @org.springframework.boot.context.properties.bind.DefaultValue("10s")
                Duration reportInterval,
        @org.springframework.boot.context.properties.bind.DefaultValue("30") int reportsDaily,
        @org.springframework.boot.context.properties.bind.DefaultValue("100")
                int commentUploadsDaily) {}

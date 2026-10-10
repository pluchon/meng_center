package com.pluchon.meng.content.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meng.content")
public record ContentProperties(
    String imageRoot,
    int likeWeight,
    Duration postInterval,
    int postsDaily,
    int uploadsDaily,
    String timezone,
    @org.springframework.boot.context.properties.bind.DefaultValue("3s")
    Duration draftInterval,
    @org.springframework.boot.context.properties.bind.DefaultValue("1")
    int viewWeight
) {}

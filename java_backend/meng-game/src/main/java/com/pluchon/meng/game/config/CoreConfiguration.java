package com.pluchon.meng.game.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// 业务时钟配置
@Configuration
public class CoreConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}

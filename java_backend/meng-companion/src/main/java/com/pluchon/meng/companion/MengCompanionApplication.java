package com.pluchon.meng.companion;

import com.pluchon.meng.companion.config.CompanionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 萌萌的权威业务状态入口
@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(CompanionProperties.class)
public class MengCompanionApplication {
    public static void main(String[] args) {
        SpringApplication.run(MengCompanionApplication.class, args);
    }
}

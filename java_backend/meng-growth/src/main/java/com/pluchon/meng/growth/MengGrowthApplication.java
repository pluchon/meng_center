package com.pluchon.meng.growth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 成长域服务启动入口
@SpringBootApplication
@ConfigurationPropertiesScan
@org.springframework.cloud.openfeign.EnableFeignClients
public class MengGrowthApplication {

    public static void main(String[] args) {
        SpringApplication.run(MengGrowthApplication.class, args);
    }
}

package com.pluchon.meng.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 网关启动入口
@SpringBootApplication
@ConfigurationPropertiesScan
public class MengGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MengGatewayApplication.class, args);
    }
}

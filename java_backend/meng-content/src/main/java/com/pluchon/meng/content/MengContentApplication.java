package com.pluchon.meng.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 内容域服务启动入口
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
public class MengContentApplication {

    public static void main(String[] args) {
        SpringApplication.run(MengContentApplication.class, args);
    }
}

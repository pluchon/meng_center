package com.pluchon.meng.game;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 游戏域（萌岛）服务启动入口
@SpringBootApplication
@ConfigurationPropertiesScan
public class MengGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(MengGameApplication.class, args);
    }
}

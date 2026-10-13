package com.pluchon.meng.im;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

// 消息域启动入口
@SpringBootApplication
@EnableFeignClients
public class MengImApplication {

    public static void main(String[] args) {
        SpringApplication.run(MengImApplication.class, args);
    }
}

package com.pluchon.meng.user;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// 用户服务启动入口
@SpringBootApplication
@ConfigurationPropertiesScan
public class MengUserApplication {

    public static void main(String[] args) {
        // Nacos 里的邮件和短信文案是中文，默认编码不是 UTF-8 时会发出乱码
        if (!StandardCharsets.UTF_8.equals(Charset.defaultCharset())) {
            throw new IllegalStateException(
                "Start with -Dfile.encoding=UTF-8; Nacos configuration contains UTF-8 text."
            );
        }
        SpringApplication.run(MengUserApplication.class, args);
    }
}

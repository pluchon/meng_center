package com.pluchon.meng.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MengUserApplication {

    public static void main(String[] args) {
        if (
            !java.nio.charset.StandardCharsets.UTF_8.equals(java.nio.charset.Charset.defaultCharset())
        ) throw new IllegalStateException("Start with -Dfile.encoding=UTF-8; Nacos configuration contains UTF-8 text.");
        SpringApplication.run(MengUserApplication.class, args);
    }
}

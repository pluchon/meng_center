package com.pluchon.meng.content.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// 时钟、消息转换和数据库分页配置
@Configuration
public class CoreConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    MybatisPlusInterceptor pagination() {
        var i = new MybatisPlusInterceptor();
        var p = new PaginationInnerInterceptor(DbType.MYSQL);
        p.setMaxLimit(50L);
        i.addInnerInterceptor(p);
        return i;
    }
}

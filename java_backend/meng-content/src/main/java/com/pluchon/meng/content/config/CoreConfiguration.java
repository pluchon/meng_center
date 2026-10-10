package com.pluchon.meng.content.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.Clock;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.*;

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

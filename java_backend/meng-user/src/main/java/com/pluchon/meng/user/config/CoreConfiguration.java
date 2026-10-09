package com.pluchon.meng.user.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.common.core.security.JwtCodec;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 用户服务的基础组件：时钟、令牌编解码和分页插件
@Configuration
public class CoreConfiguration {

    // 分页插件的每页上限，接口层另有参数校验，这里兜底
    private static final long PAGE_SIZE_LIMIT = 100L;

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    JwtCodec jwtCodec() {
        return new JwtCodec(System.getenv(AuthConstants.JWT_SECRET_ENV));
    }

    @Bean
    MybatisPlusInterceptor pagination() {
        var interceptor = new MybatisPlusInterceptor();
        var pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(PAGE_SIZE_LIMIT);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}

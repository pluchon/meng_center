package com.pluchon.meng.gateway.config;

import com.pluchon.meng.common.core.security.*;
import org.springframework.context.annotation.*;

// 网关的公共组件装配
@Configuration
public class GatewayConfiguration {

    @Bean
    JwtCodec jwtCodec() {
        return new JwtCodec(System.getenv(AuthConstants.JWT_SECRET_ENV));
    }
}

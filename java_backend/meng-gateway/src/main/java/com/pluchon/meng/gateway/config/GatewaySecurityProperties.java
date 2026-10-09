package com.pluchon.meng.gateway.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 网关鉴权配置：允许的来源站点、会话续期时长、免登录白名单和公开读接口
@Validated
@ConfigurationProperties("meng.gateway.security")
public record GatewaySecurityProperties(
    @NotEmpty List<String> allowedOrigins,
    @NotNull Duration sessionTtl,
    @Valid @NotEmpty List<Endpoint> whitelist,
    @Valid List<Endpoint> publicRead
) {
    public record Endpoint(@NotBlank String method, @NotBlank String path) {}
}

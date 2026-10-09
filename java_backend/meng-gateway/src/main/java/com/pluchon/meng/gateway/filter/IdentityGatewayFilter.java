package com.pluchon.meng.gateway.filter;

import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.common.core.security.IdentityHeaders;
import com.pluchon.meng.common.core.security.JwtCodec;
import com.pluchon.meng.common.core.security.SessionKey;
import com.pluchon.meng.gateway.config.GatewaySecurityProperties;
import com.pluchon.meng.gateway.service.GatewaySessionService;
import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// 统一鉴权：清掉客户端自带的身份头，校验来源与会话，再把可信身份写进请求头交给下游
@Component
public final class IdentityGatewayFilter implements WebFilter, Ordered {

    // 排在路由之前执行
    private static final int ORDER = -100;
    private static final String UNKNOWN_IP = "unknown";
    // 这些头只能由网关写入，客户端带来的一律丢弃
    private static final List<String> UNTRUSTED_HEADERS = List.of(
        IdentityHeaders.USER_ID,
        IdentityHeaders.USER_ROLE,
        IdentityHeaders.SESSION_ID,
        IdentityHeaders.CLIENT_IP,
        "X-Forwarded-For",
        "Forwarded"
    );

    @Autowired
    private GatewaySecurityProperties properties;

    @Autowired
    private JwtCodec jwt;

    @Autowired
    private GatewaySessionService sessions;

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange original, WebFilterChain chain) {
        String ip = clientIp(original.getRequest());
        ServerHttpRequest clean = original
            .getRequest()
            .mutate()
            .headers(headers -> {
                UNTRUSTED_HEADERS.forEach(headers::remove);
                headers.set(IdentityHeaders.CLIENT_IP, ip);
            })
            .build();
        ServerWebExchange exchange = original.mutate().request(clean).build();
        String path;
        try {
            path = PathPolicy.normalize(clean.getURI().getRawPath());
        } catch (IllegalArgumentException exception) {
            return GatewayResponses.failure(exchange, CommonErrorCode.INVALID_ARGUMENT);
        }
        // 内部接口只给服务之间调用，任何写法的 internal 路径都不对外
        if (PathPolicy.internal(path)) {
            return GatewayResponses.failure(exchange, CommonErrorCode.FORBIDDEN);
        }
        HttpMethod method = clean.getMethod();
        // 令牌放在 Cookie 里，写操作必须来自允许的站点，防跨站请求伪造
        if (method != HttpMethod.GET && !properties.allowedOrigins().contains(clean.getHeaders().getOrigin())) {
            return GatewayResponses.failure(exchange, CommonErrorCode.FORBIDDEN);
        }
        if (matches(properties.whitelist(), method, path)) {
            return chain.filter(exchange);
        }
        HttpCookie cookie = clean.getCookies().getFirst(AuthConstants.TOKEN_COOKIE);
        if (cookie == null) {
            return matches(properties.publicRead(), method, path)
                ? chain.filter(exchange)
                : GatewayResponses.failure(exchange, CommonErrorCode.UNAUTHENTICATED);
        }
        SessionKey key;
        try {
            key = jwt.decode(cookie.getValue());
        } catch (RuntimeException exception) {
            return GatewayResponses.failure(exchange, CommonErrorCode.UNAUTHENTICATED);
        }
        return sessions
            .resolve(key)
            .flatMap(identity -> forward(exchange, chain, path, identity).thenReturn(true))
            .switchIfEmpty(GatewayResponses.failure(exchange, CommonErrorCode.UNAUTHENTICATED).thenReturn(false))
            .then();
    }

    // 会话有效：管理路径再核对角色，然后带着身份头转发
    private Mono<Void> forward(
        ServerWebExchange exchange,
        WebFilterChain chain,
        String path,
        GatewaySessionService.Identity identity
    ) {
        if (PathPolicy.admin(path) && !AuthConstants.ADMIN_ROLE.equals(identity.role())) {
            return GatewayResponses.failure(exchange, CommonErrorCode.FORBIDDEN);
        }
        ServerHttpRequest request = exchange
            .getRequest()
            .mutate()
            .headers(headers -> {
                headers.set(IdentityHeaders.USER_ID, Long.toString(identity.userId()));
                headers.set(IdentityHeaders.USER_ROLE, identity.role());
                headers.set(IdentityHeaders.SESSION_ID, identity.sessionId());
            })
            .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    // 来源地址只取直连对端，不信任转发头
    private String clientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        return remote == null || remote.getAddress() == null ? UNKNOWN_IP : remote.getAddress().getHostAddress();
    }

    private boolean matches(List<GatewaySecurityProperties.Endpoint> endpoints, HttpMethod method, String path) {
        return (
            endpoints != null &&
            endpoints
                .stream()
                .anyMatch(endpoint -> endpoint.method().equals(method.name()) && endpoint.path().equals(path))
        );
    }
}

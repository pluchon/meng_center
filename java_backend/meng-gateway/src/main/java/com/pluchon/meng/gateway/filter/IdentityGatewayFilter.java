package com.pluchon.meng.gateway.filter;

import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.response.ApiResponse;
import com.pluchon.meng.common.core.security.*;
import com.pluchon.meng.gateway.config.GatewaySecurityProperties;
import com.pluchon.meng.gateway.service.GatewaySessionService;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class IdentityGatewayFilter implements WebFilter, Ordered {

    private final GatewaySecurityProperties properties;
    private final JwtCodec jwt;
    private final GatewaySessionService sessions;
    private final JsonMapper json = JsonMapper.builder().build();

    public IdentityGatewayFilter(GatewaySecurityProperties properties, JwtCodec jwt, GatewaySessionService sessions) {
        this.properties = properties;
        this.jwt = jwt;
        this.sessions = sessions;
    }

    public int getOrder() {
        return -100;
    }

    public Mono<Void> filter(ServerWebExchange original, WebFilterChain chain) {
        ServerHttpRequest clean = original
            .getRequest()
            .mutate()
            .headers(headers -> {
                headers.remove(IdentityHeaders.USER_ID);
                headers.remove(IdentityHeaders.USER_ROLE);
                headers.remove(IdentityHeaders.SESSION_ID);
                headers.remove(IdentityHeaders.CLIENT_IP);
                headers.remove("X-Forwarded-For");
                headers.remove("Forwarded");
            })
            .build();
        var remote = original.getRequest().getRemoteAddress();
        String ip = remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
        clean = clean.mutate().header(IdentityHeaders.CLIENT_IP, ip).build();
        var exchange = original.mutate().request(clean).build();
        String path;
        try {
            path = PathPolicy.normalize(clean.getURI().getRawPath());
        } catch (IllegalArgumentException exception) {
            return reject(exchange, CommonErrorCode.INVALID_ARGUMENT);
        }
        if (PathPolicy.internal(path)) return reject(exchange, CommonErrorCode.FORBIDDEN);
        HttpMethod method = clean.getMethod();
        if (
            method != HttpMethod.GET && !properties.allowedOrigins().contains(clean.getHeaders().getOrigin())
        ) return reject(exchange, CommonErrorCode.FORBIDDEN);
        if (matches(properties.whitelist(), method, path)) return chain.filter(exchange);
        boolean publicRead = matches(properties.publicRead(), method, path);
        HttpCookie cookie = clean.getCookies().getFirst(AuthConstants.TOKEN_COOKIE);
        if (cookie == null) return publicRead
            ? chain.filter(exchange)
            : reject(exchange, CommonErrorCode.UNAUTHENTICATED);
        SessionKey key;
        try {
            key = jwt.decode(cookie.getValue());
        } catch (RuntimeException exception) {
            return reject(exchange, CommonErrorCode.UNAUTHENTICATED);
        }
        return sessions
            .resolve(key)
            .flatMap(identity -> {
                if (PathPolicy.admin(path) && !AuthConstants.ADMIN_ROLE.equals(identity.role())) return reject(
                    exchange,
                    CommonErrorCode.FORBIDDEN
                ).thenReturn(false);
                var request = exchange
                    .getRequest()
                    .mutate()
                    .headers(headers -> {
                        headers.set(IdentityHeaders.USER_ID, Long.toString(identity.userId()));
                        headers.set(IdentityHeaders.USER_ROLE, identity.role());
                        headers.set(IdentityHeaders.SESSION_ID, identity.sessionId());
                    })
                    .build();
                return chain.filter(exchange.mutate().request(request).build()).thenReturn(true);
            })
            .switchIfEmpty(reject(exchange, CommonErrorCode.UNAUTHENTICATED).thenReturn(false))
            .then();
    }

    private boolean matches(
        java.util.List<GatewaySecurityProperties.Endpoint> endpoints,
        HttpMethod method,
        String path
    ) {
        return (
            endpoints != null &&
            endpoints
                .stream()
                .anyMatch(endpoint -> endpoint.method().equals(method.name()) && endpoint.path().equals(path))
        );
    }

    private Mono<Void> reject(ServerWebExchange exchange, CommonErrorCode code) {
        return Mono.defer(() -> {
            if (exchange.getResponse().isCommitted()) return Mono.empty();
            exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(code.httpStatus()));
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = json.writeValueAsBytes(ApiResponse.failure(code, null));
            return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
        });
    }
}

package com.pluchon.meng.gateway.service;

import com.pluchon.meng.common.core.security.*;
import com.pluchon.meng.gateway.config.GatewaySecurityProperties;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public final class GatewaySessionService {

    private final ReactiveStringRedisTemplate redis;
    private final GatewaySecurityProperties properties;
    private final DefaultRedisScript<String> resolve = new DefaultRedisScript<>();

    public GatewaySessionService(ReactiveStringRedisTemplate redis, GatewaySecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
        resolve.setLocation(new ClassPathResource("redis/resolve-session.lua"));
        resolve.setResultType(String.class);
    }

    public Mono<Identity> resolve(SessionKey session) {
        return redis
            .execute(
                resolve,
                List.of(
                    AuthConstants.SESSION_PREFIX + session.sessionId(),
                    AuthConstants.USER_SESSIONS_PREFIX + session.userId()
                ),
                List.of(Long.toString(session.userId()), Long.toString(properties.sessionTtl().toSeconds()))
            )
            .next()
            .filter(value -> !value.isBlank())
            .map(role -> new Identity(session.userId(), role, session.sessionId()));
    }

    public record Identity(long userId, String role, String sessionId) {}
}

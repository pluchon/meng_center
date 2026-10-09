package com.pluchon.meng.gateway.service;

import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.common.core.security.SessionKey;
import com.pluchon.meng.gateway.config.GatewaySecurityProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

// 网关侧的会话解析：一次 Redis 脚本完成校验和续期
@Service
public final class GatewaySessionService {

    // 校验会话归属与账号状态，通过则续期并返回角色，否则返回空串
    private final DefaultRedisScript<String> resolve = script();

    @Autowired
    private ReactiveStringRedisTemplate redis;

    @Autowired
    private GatewaySecurityProperties properties;

    private static DefaultRedisScript<String> script() {
        var script = new DefaultRedisScript<String>();
        script.setLocation(new ClassPathResource("redis/resolve-session.lua"));
        script.setResultType(String.class);
        return script;
    }

    // 会话不存在、不属于该用户或账号不正常时结果为空
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

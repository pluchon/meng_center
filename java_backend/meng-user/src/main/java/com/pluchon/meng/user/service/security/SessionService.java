package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.common.core.security.JwtCodec;
import com.pluchon.meng.common.core.security.SessionKey;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.UserEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public final class SessionService {

    private final StringRedisTemplate redis;
    private final JwtCodec jwt;
    private final UserProperties properties;
    private final DefaultRedisScript<Long> create = script("redis/create-session.lua");
    private final DefaultRedisScript<Long> revokeAll = script("redis/revoke-sessions.lua");
    private final DefaultRedisScript<Long> revokeOne = script("redis/revoke-session.lua");

    public SessionService(StringRedisTemplate redis, JwtCodec jwt, UserProperties properties) {
        this.redis = redis;
        this.jwt = jwt;
        this.properties = properties;
    }

    private static DefaultRedisScript<Long> script(String location) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    public String create(UserEntity user) {
        String id = UUID.randomUUID().toString();
        redis.execute(
            create,
            List.of(AuthConstants.SESSION_PREFIX + id, AuthConstants.USER_SESSIONS_PREFIX + user.getId()),
            user.getId().toString(),
            user.getRole().name(),
            user.getStatus().name(),
            id,
            Long.toString(properties.session().ttl().toSeconds())
        );
        return jwt.encode(new SessionKey(user.getId(), id));
    }

    public void revoke(long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        redis.execute(
            revokeOne,
            List.of(AuthConstants.SESSION_PREFIX + sessionId, AuthConstants.USER_SESSIONS_PREFIX + userId),
            sessionId
        );
    }

    public void revokeAll(long userId) {
        redis.execute(revokeAll, List.of(AuthConstants.USER_SESSIONS_PREFIX + userId), AuthConstants.SESSION_PREFIX);
    }
}

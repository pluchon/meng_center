package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.common.core.security.JwtCodec;
import com.pluchon.meng.common.core.security.SessionKey;
import com.pluchon.meng.common.web.region.IpRegionService;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.UserEntity;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

// 会话存 Redis：令牌里只有用户编号和会话编号，有效期与撤销都由 Redis 决定
@Service
public final class SessionService {

    private final DefaultRedisScript<Long> create = RedisScripts.counting(
        "redis/create-session.lua"
    );
    private final DefaultRedisScript<Long> revokeOne = RedisScripts.counting(
        "redis/revoke-session.lua"
    );
    private final DefaultRedisScript<Long> revokeAll = RedisScripts.counting(
        "redis/revoke-sessions.lua"
    );

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JwtCodec jwt;

    @Autowired
    private UserProperties properties;

    @Autowired
    private IpRegionService regions;

    // 建一个新会话并登记到该用户的会话集合，返回令牌
    public String create(UserEntity user) {
        return create(user, null, "");
    }

    public String create(UserEntity user, String ip, String agent) {
        String id = UUID.randomUUID().toString();
        redis.execute(
            create,
            List.of(
                AuthConstants.SESSION_PREFIX + id,
                AuthConstants.USER_SESSIONS_PREFIX + user.getId()
            ),
            user.getId().toString(),
            user.getRole().name(),
            user.getStatus().name(),
            id,
            Long.toString(properties.session().ttl().toSeconds())
        );
        long now = Instant.now().getEpochSecond();
        redis
            .opsForHash()
            .putAll(
                AuthConstants.SESSION_PREFIX + id,
                Map.of(
                    "deviceId",
                    UUID.randomUUID().toString(),
                    "deviceName",
                    deviceName(agent),
                    "region",
                    java.util.Objects.toString(regions.resolve(ip), ""),
                    "loginAt",
                    Long.toString(now),
                    "lastActiveAt",
                    Long.toString(now)
                )
            );
        return jwt.encode(new SessionKey(user.getId(), id));
    }

    static String deviceName(String agent) {
        String ua = agent == null ? "" : agent;
        String os = ua.contains("Android")
            ? "Android"
            : ua.contains("iPhone") || ua.contains("iPad")
              ? "iOS"
              : ua.contains("Windows")
                ? "Windows"
                : ua.contains("Macintosh")
                  ? "macOS"
                  : ua.contains("Linux")
                    ? "Linux"
                    : "未知系统";
        String browser = ua.contains("Edg/")
            ? "Edge"
            : ua.contains("Firefox/")
              ? "Firefox"
              : ua.contains("Chrome/")
                ? "Chrome"
                : ua.contains("Safari/")
                  ? "Safari"
                  : "未知浏览器";
        return ua.isBlank() ? "未知设备" : os + " · " + browser;
    }

    // 退出当前设备
    public void revoke(long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        redis.execute(
            revokeOne,
            List.of(
                AuthConstants.SESSION_PREFIX + sessionId,
                AuthConstants.USER_SESSIONS_PREFIX + userId
            ),
            sessionId
        );
    }

    // 改密或重置密码后让所有设备下线
    public void revokeAll(long userId) {
        redis.execute(
            revokeAll,
            List.of(AuthConstants.USER_SESSIONS_PREFIX + userId),
            AuthConstants.SESSION_PREFIX
        );
    }
}

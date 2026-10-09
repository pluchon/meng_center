package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.user.config.UserProperties;
import java.time.Duration;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public final class RateLimitService {

    private static final String PREFIX = "meng:limit:";
    private final StringRedisTemplate redis;
    private final UserProperties properties;
    private final PiiCipher cipher;
    private final DefaultRedisScript<Long> increment;

    public RateLimitService(StringRedisTemplate redis, UserProperties properties, PiiCipher cipher) {
        this.redis = redis;
        this.properties = properties;
        this.cipher = cipher;
        increment = new DefaultRedisScript<>();
        increment.setLocation(new ClassPathResource("redis/increment-limit.lua"));
        increment.setResultType(Long.class);
    }

    public void charge(String scope, String identity, long max, Duration duration) {
        Long count = redis.execute(
            increment,
            List.of(PREFIX + scope + ":" + cipher.hash(identity)),
            Long.toString(duration.toSeconds())
        );
        if (count == null || count > max) throw new BusinessException(CommonErrorCode.RATE_LIMITED);
    }

    public void send(ContactService.Contact contact, String ip) {
        charge("send-interval", contact.hash(), 1, properties.limits().sendInterval());
        charge("send-window", contact.hash(), properties.limits().sendWindowMax(), properties.limits().sendWindow());
        if (contact.channel() == com.pluchon.meng.user.entity.enums.ContactChannel.SMS) charge(
            "sms-ip",
            ip,
            properties.limits().smsIpDailyMax(),
            Duration.ofDays(1)
        );
    }

    public int loginFailures(String username) {
        String value = redis.opsForValue().get(PREFIX + "login:" + cipher.hash(username));
        return value == null ? 0 : Integer.parseInt(value);
    }

    public void failedLogin(String username) {
        String key = PREFIX + "login:" + cipher.hash(username);
        Long count = redis.execute(
            increment,
            List.of(key),
            Long.toString(properties.limits().loginLockDuration().toSeconds())
        );
        if (count != null && count == properties.limits().loginMaxFailures())
            redis.expire(key, properties.limits().loginLockDuration());
    }

    public void clearLogin(String username) {
        redis.delete(PREFIX + "login:" + cipher.hash(username));
    }
}

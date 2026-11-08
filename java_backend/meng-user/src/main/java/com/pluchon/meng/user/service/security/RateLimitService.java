package com.pluchon.meng.user.service.security;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.user.config.UserProperties;
import com.pluchon.meng.user.entity.enums.ContactChannel;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

// 基于 Redis 计数的限流：发码间隔、发码窗口、按地址的日上限和登录失败锁定
@Service
public final class RateLimitService {

    private static final String PREFIX = "meng:limit:";
    private static final String LOGIN_SCOPE = "login";
    private static final Duration DAY = Duration.ofDays(1);

    // 计数加一，首次计数时设置过期
    private final DefaultRedisScript<Long> increment = RedisScripts.counting("redis/increment-limit.lua");

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private UserProperties properties;

    @Autowired
    private PiiCipher cipher;

    // 限流键里不放明文标识
    private String key(String scope, String identity) {
        return PREFIX + scope + ":" + cipher.hash(identity);
    }

    private Long increase(String key, Duration duration) {
        return redis.execute(increment, List.of(key), Long.toString(duration.toSeconds()));
    }

    // 计数超过上限就拒绝；Redis 没有返回结果时也按拒绝处理
    public void charge(String scope, String identity, long max, Duration duration) {
        Long count = increase(key(scope, identity), duration);
        if (count == null || count > max) {
            throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        }
    }

    // 按天计数的上限
    public void chargeDaily(String scope, String identity, long max) {
        charge(scope, identity, max, DAY);
    }

    // 发验证码：同一联系方式有间隔和窗口上限，短信另加来源地址的日上限
    public void send(ContactService.Contact contact, String ip) {
        var limits = properties.limits();
        charge("send-interval", contact.hash(), 1, limits.sendInterval());
        charge("send-window", contact.hash(), limits.sendWindowMax(), limits.sendWindow());
        if (contact.channel() == ContactChannel.SMS) {
            chargeDaily("sms-ip", ip, limits.smsIpDailyMax());
        }
    }

    // 计数大于零才减一，不改过期时间
    private static final DefaultRedisScript<Long> DECREMENT = new DefaultRedisScript<>(
        "local v=tonumber(redis.call('GET',KEYS[1]) or '0');if v>0 then return redis.call('DECR',KEYS[1]) end;return 0",
        Long.class
    );

    // 投递失败时退回本次发码占用的间隔、窗口和短信日额度，用户不用干等就能重试
    public void refundSend(ContactService.Contact contact, String ip) {
        for (String key : sendKeys(contact, ip)) redis.execute(DECREMENT, List.of(key));
    }

    private List<String> sendKeys(ContactService.Contact contact, String ip) {
        var keys = new java.util.ArrayList<>(List.of(key("send-interval", contact.hash()), key("send-window", contact.hash())));
        if (contact.channel() == ContactChannel.SMS) keys.add(key("sms-ip", ip));
        return keys;
    }

    // 该用户名是否已因连续输错密码被锁定
    public boolean loginLocked(String username) {
        String value = redis.opsForValue().get(key(LOGIN_SCOPE, username));
        return value != null && Integer.parseInt(value) >= properties.limits().loginMaxFailures();
    }

    // 记一次登录失败；达到上限的那一次重新开始计锁定时长
    public void failedLogin(String username) {
        var limits = properties.limits();
        String key = key(LOGIN_SCOPE, username);
        Long count = increase(key, limits.loginLockDuration());
        if (count != null && count == limits.loginMaxFailures()) {
            redis.expire(key, limits.loginLockDuration());
        }
    }

    // 登录成功或重置密码后清掉失败计数
    public void clearLogin(String username) {
        redis.delete(key(LOGIN_SCOPE, username));
    }
}

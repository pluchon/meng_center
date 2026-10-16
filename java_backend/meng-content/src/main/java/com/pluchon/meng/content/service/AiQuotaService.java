package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

// 按用户原子校验每日与分钟配额
@Service
public class AiQuotaService {

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    private static final DefaultRedisScript<Long> TAKE = new DefaultRedisScript<>(
        "local d=tonumber(redis.call('GET',KEYS[1]) or '0');local m=tonumber(redis.call('GET',KEYS[2]) or '0');" +
            "if d>=tonumber(ARGV[1]) or m>=tonumber(ARGV[2]) then return 0 end;" +
            "redis.call('INCR',KEYS[1]);redis.call('EXPIRE',KEYS[1],ARGV[3]);" +
            "redis.call('INCR',KEYS[2]);redis.call('EXPIRE',KEYS[2],120);return 1",
        Long.class
    );

    // 原子扣减每日和分钟额度，超限明确拒绝
    public void take(String feature, long user, int daily, int minute) {
        var now = clock.instant().atZone(ZoneId.of("Asia/Taipei"));
        long ttl = Duration.between(
            now,
            now.toLocalDate().plusDays(1).atStartOfDay(now.getZone())
        ).toSeconds();
        String root = "content:ai:quota:" + feature + ":" + user + ":";
        if (
            !Long.valueOf(1).equals(
                redis.execute(
                    TAKE,
                    List.of(
                        root + now.toLocalDate(),
                        root + "minute:" + clock.instant().getEpochSecond() / 60
                    ),
                    String.valueOf(daily),
                    String.valueOf(minute),
                    String.valueOf(Math.max(1, ttl))
                )
            )
        ) throw new BusinessException(CommonErrorCode.RATE_LIMITED);
    }
}

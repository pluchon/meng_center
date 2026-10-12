package com.pluchon.meng.im.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

// Redis 原子计数，故障时不影响私信数据库写入
@Service
public class ImRateLimit {

    private static final Logger LOG = LoggerFactory.getLogger(
        ImRateLimit.class
    );
    private static final DefaultRedisScript<Long> COUNT =
        new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end; return n",
            Long.class
        );

    @Autowired
    private StringRedisTemplate redis;

    public void message(long user) {
        check("meng:im:rate:msg:" + user, 20, Duration.ofMinutes(1));
    }

    public void image(long user) {
        check(
            "meng:im:rate:image:" +
                user +
                ":" +
                LocalDate.now(ZoneId.of("Asia/Taipei")),
            100,
            Duration.ofDays(1)
        );
    }

    private void check(String key, long max, Duration ttl) {
        Long count;
        try {
            count = redis.execute(
                COUNT,
                List.of(key),
                Long.toString(ttl.toSeconds())
            );
        } catch (RuntimeException failure) {
            LOG.warn("IM rate counter unavailable");
            return;
        }
        if (count != null && count > max) throw new BusinessException(
            CommonErrorCode.RATE_LIMITED
        );
    }
}

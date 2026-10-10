package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.*;
import com.pluchon.meng.content.config.ContentProperties;
import java.time.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContentLimitService {

    private final StringRedisTemplate redis;
    private final ContentProperties properties;
    private final Clock clock;
    private final DefaultRedisScript<Long> count = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]);if n==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]) end;return n",
        Long.class
    );

    private void charge(String scope, long user, int max, long seconds) {
        Long n = redis.execute(
            count,
            List.of("meng:content:limit:" + scope + ":" + user),
            Long.toString(seconds)
        );
        if (n == null || n > max) throw new BusinessException(
            CommonErrorCode.RATE_LIMITED
        );
    }

    public void post(long user) {
        charge("post-interval", user, 1, properties.postInterval().toSeconds());
        daily("post", user, properties.postsDaily());
    }

    public void upload(long user) {
        daily("upload", user, properties.uploadsDaily());
    }

    public void draft(long user) {
        charge(
            "draft-interval",
            user,
            1,
            Math.max(1, properties.draftInterval().toSeconds())
        );
    }

    private void daily(String scope, long user, int max) {
        var now = clock.instant().atZone(ZoneId.of(properties.timezone()));
        charge(
            scope + ":" + now.toLocalDate(),
            user,
            max,
            Math.max(
                1,
                Duration.between(
                    now,
                    now.toLocalDate().plusDays(1).atStartOfDay(now.getZone())
                ).toSeconds()
            )
        );
    }
}

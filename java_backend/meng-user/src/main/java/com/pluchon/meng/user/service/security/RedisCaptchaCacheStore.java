package com.pluchon.meng.user.service.security;

import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public final class RedisCaptchaCacheStore implements CacheStore {

    private final StringRedisTemplate redis;
    private final Gson gson = new Gson();

    public RedisCaptchaCacheStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public AnyMap getCache(String key) {
        return parse(redis.opsForValue().get(key));
    }

    public AnyMap getAndRemoveCache(String key) {
        return parse(redis.opsForValue().getAndDelete(key));
    }

    private AnyMap parse(String json) {
        return json == null ? null : gson.fromJson(json, AnyMap.class);
    }

    public boolean setCache(String key, AnyMap data, Long expire, TimeUnit unit) {
        redis.opsForValue().set(key, gson.toJson(data), expire, unit);
        return true;
    }

    public Long incr(String key, long delta, Long expire, TimeUnit unit) {
        var script = new DefaultRedisScript<Long>(
            "local n=redis.call('INCRBY',KEYS[1],ARGV[1]);if n==tonumber(ARGV[1]) then redis.call('PEXPIRE',KEYS[1],ARGV[2]) end;return n",
            Long.class
        );
        return redis.execute(script, List.of(key), Long.toString(delta), Long.toString(unit.toMillis(expire)));
    }

    public Long getLong(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? null : Long.valueOf(value);
    }

    public void close() {}
}

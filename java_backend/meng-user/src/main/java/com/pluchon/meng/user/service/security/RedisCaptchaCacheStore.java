package com.pluchon.meng.user.service.security;

import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.common.AnyMap;
import com.google.gson.Gson;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

// 滑块答案存 Redis，多实例之间共用，取出即删
@Component
public final class RedisCaptchaCacheStore implements CacheStore {

    private static final Gson GSON = new Gson();

    // 按步长累加，首次累加时设置毫秒级过期
    private final DefaultRedisScript<Long> incrementBy = RedisScripts.counting("redis/increment-by.lua");

    @Autowired
    private StringRedisTemplate redis;

    @Override
    public AnyMap getCache(String key) {
        return parse(redis.opsForValue().get(key));
    }

    @Override
    public AnyMap getAndRemoveCache(String key) {
        return parse(redis.opsForValue().getAndDelete(key));
    }

    private AnyMap parse(String json) {
        return json == null ? null : GSON.fromJson(json, AnyMap.class);
    }

    @Override
    public boolean setCache(String key, AnyMap data, Long expire, TimeUnit unit) {
        redis.opsForValue().set(key, GSON.toJson(data), expire, unit);
        return true;
    }

    @Override
    public Long incr(String key, long delta, Long expire, TimeUnit unit) {
        return redis.execute(incrementBy, List.of(key), Long.toString(delta), Long.toString(unit.toMillis(expire)));
    }

    @Override
    public Long getLong(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? null : Long.valueOf(value);
    }

    @Override
    public void close() {}
}

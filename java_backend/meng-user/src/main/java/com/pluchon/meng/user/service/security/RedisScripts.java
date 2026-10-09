package com.pluchon.meng.user.service.security;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

// Redis Lua 脚本的统一加载入口：脚本对象只建一次，摘要只算一次
public final class RedisScripts {

    private RedisScripts() {}

    // 读取 classpath 下返回整数的脚本
    public static DefaultRedisScript<Long> counting(String location) {
        var script = new DefaultRedisScript<Long>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }
}

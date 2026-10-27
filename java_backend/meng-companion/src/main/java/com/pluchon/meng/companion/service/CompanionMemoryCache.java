package com.pluchon.meng.companion.service;

import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** 记忆正文与向量的热缓存；数据库版本约束并发失效后的旧缓存回写。 */
@Service
public class CompanionMemoryCache {

    private static final Logger LOG = LoggerFactory.getLogger(
        CompanionMemoryCache.class
    );
    private static final Duration TTL = Duration.ofHours(1);
    private final CompanionMapper mapper;
    private final StringRedisTemplate redis;
    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public CompanionMemoryCache(
        CompanionMapper mapper,
        StringRedisTemplate redis
    ) {
        this.mapper = mapper;
        this.redis = redis;
    }

    public record Cached(
        String characterCode,
        long revision,
        long profileRevision,
        List<Rows.Memory> memories
    ) {}

    public List<Rows.Memory> get(long user, String code) {
        var bond = mapper.bond(user, code);
        if (bond == null) return List.of();
        String key = "meng:companion:memo:" + user;
        try {
            String raw = redis.opsForValue().get(key);
            if (raw != null) {
                var cached = json.readValue(raw, Cached.class);
                if (
                    code.equals(cached.characterCode()) &&
                    cached.revision() == bond.getRevision() &&
                    cached.profileRevision() == bond.getProfileRevision() &&
                    cached.memories() != null &&
                    cached
                        .memories()
                        .stream()
                        .allMatch(
                            m -> m.getId() != null && m.getContent() != null
                        )
                ) {
                    return cached.memories();
                }
            }
        } catch (RuntimeException error) {
            LOG.warn(
                "Memory cache read skipped error={}",
                error.getClass().getSimpleName()
            );
        }
        var memories = mapper.memories(user, code);
        try {
            redis
                .opsForValue()
                .set(
                    key,
                    json.writeValueAsString(
                        new Cached(
                            code,
                            bond.getRevision(),
                            bond.getProfileRevision(),
                            memories
                        )
                    ),
                    TTL
                );
        } catch (RuntimeException error) {
            LOG.warn(
                "Memory cache write skipped error={}",
                error.getClass().getSimpleName()
            );
        }
        return memories;
    }
}

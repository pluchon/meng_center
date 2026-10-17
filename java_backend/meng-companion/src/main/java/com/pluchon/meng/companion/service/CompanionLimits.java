package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.companion.config.CompanionProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

// Redis 脚本原子预留，失败释放；过期的预留不计入新请求
@Service
public class CompanionLimits {
    @Autowired
    private StringRedisTemplate redis;
    @Autowired
    private Clock clock;
    @Autowired
    private CompanionProperties properties;

    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
        redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',ARGV[1]-120000)
        redis.call('ZREMRANGEBYSCORE',KEYS[3],'-inf',ARGV[1]-60000)
        local used=tonumber(redis.call('HGET',KEYS[1],ARGV[5]) or '0')
        if used+redis.call('ZCARD',KEYS[2]) >= tonumber(ARGV[3]) or redis.call('ZCARD',KEYS[3]) >= tonumber(ARGV[4]) then return 0 end
        redis.call('ZADD',KEYS[2],ARGV[1],ARGV[2])
        redis.call('ZADD',KEYS[3],ARGV[1],ARGV[2])
        redis.call('EXPIRE',KEYS[2],172800)
        redis.call('EXPIRE',KEYS[3],120)
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>("""
        if redis.call('ZREM',KEYS[2],ARGV[1]) == 0 then return 0 end
        if ARGV[3] == '1' then
          redis.call('HINCRBY',KEYS[1],ARGV[2],1)
          redis.call('EXPIRE',KEYS[1],172800)
        else redis.call('ZREM',KEYS[3],ARGV[1]) end
        return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>("""
        if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> PLAY = new DefaultRedisScript<>("""
        local last=tonumber(redis.call('HGET',KEYS[1],'last') or ARGV[1])
        local gap=tonumber(ARGV[1])-last
        local delta=0
        if gap > 0 and gap <= 600 then delta=gap end
        local chain=tonumber(redis.call('HGET',KEYS[1],'chain') or '0')
        if gap > 600 then chain=0 redis.call('HSET',KEYS[1],'reminded',0) end
        if chain+delta >= 3600 then redis.call('HSET',KEYS[1],'reminded',1) end
        redis.call('HSET',KEYS[1],'last',ARGV[1],'chain',chain+delta)
        local total=redis.call('HINCRBY',KEYS[1],'seconds',delta)
        redis.call('EXPIRE',KEYS[1],172800)
        return total
        """, Long.class);

    public record Reservation(long user, String date, String token, List<String> kinds, boolean messageAllowed) {}

    public String lock(long session) {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("meng:companion:lock:" + session, token, Duration.ofMinutes(2)))) {
            throw new BusinessException(CompanionError.COMPANION_BUSY);
        }
        return token;
    }

    public boolean locked(long session, String token) {
        return token.equals(redis.opsForValue().get("meng:companion:lock:" + session));
    }

    public void unlock(long session, String token) {
        redis.execute(UNLOCK, List.of("meng:companion:lock:" + session), token);
    }

    public void checkCooldown(long user) {
        if (Boolean.TRUE.equals(redis.hasKey("meng:companion:cooldown:" + user))) throw new BusinessException(CompanionError.COMPANION_COOLDOWN);
    }

    public void cooldown(long user) { redis.opsForValue().set("meng:companion:cooldown:" + user, "1", Duration.ofMinutes(30)); }

    public Reservation reserve(long user) {
        String date = date();
        String token = UUID.randomUUID().toString();
        List<String> kinds = new ArrayList<>();
        try {
            for (String kind : List.of("message", "document", "image")) {
                int daily = switch (kind) { case "message" -> properties.getMessageDaily(); case "document" -> properties.getDocumentDaily(); default -> properties.getImageDaily(); };
                int minute = switch (kind) { case "message" -> properties.getMessageMinute(); case "document" -> properties.getDocumentMinute(); default -> properties.getImageMinute(); };
                var result = redis.execute(RESERVE, keys(user, date, kind), Long.toString(clock.millis()), token,
                    Integer.toString(daily), Integer.toString(minute), kind);
                if (Long.valueOf(1).equals(result)) kinds.add(kind);
                else if ("message".equals(kind)) break;
            }
            return new Reservation(user, date, token, List.copyOf(kinds), kinds.contains("message"));
        } catch (RuntimeException error) {
            finish(new Reservation(user, date, token, List.copyOf(kinds), false), List.of());
            throw error;
        }
    }

    public void finish(Reservation r, List<String> used) {
        for (String kind : r.kinds()) redis.execute(FINISH, keys(r.user(), r.date(), kind), r.token(), kind, used.contains(kind) ? "1" : "0");
    }

    public List<String> tools(Reservation r) {
        var tools = new ArrayList<>(List.of("search_posts", "my_recent_posts", "my_growth"));
        if (r.kinds().contains("document")) tools.add("write_document");
        if (r.kinds().contains("image")) tools.add("draw_picture");
        return tools;
    }

    public Map<String, Object> usage(long user) {
        var counts = redis.opsForHash().entries("meng:companion:usage:" + user + ":" + date());
        long seconds = number(redis.opsForHash().get("meng:companion:play:" + user + ":" + date(), "seconds"));
        Long cooldown = redis.getExpire("meng:companion:cooldown:" + user);
        return Map.of("messages", Math.max(0, properties.getMessageDaily() - number(counts.get("message"))),
            "documents", Math.max(0, properties.getDocumentDaily() - number(counts.get("document"))),
            "images", Math.max(0, properties.getImageDaily() - number(counts.get("image"))),
            "chatSeconds", seconds, "dailyReminder", seconds >= 7200,
            "cooldownSeconds", cooldown == null ? 0L : Math.max(0L, cooldown));
    }

    public boolean restReminder(long user) {
        var map = redis.opsForHash().entries("meng:companion:play:" + user + ":" + date());
        long gap = clock.instant().getEpochSecond() - number(map.get("last"));
        return gap <= 600 && number(map.get("reminded")) == 0 && number(map.get("chain")) + Math.max(0, gap) >= 3600;
    }

    public void play(long user) {
        redis.execute(PLAY, List.of("meng:companion:play:" + user + ":" + date()), Long.toString(clock.instant().getEpochSecond()));
    }

    private long number(Object value) { return value == null ? 0 : Long.parseLong(value.toString()); }
    private String date() { return clock.instant().atZone(ZoneId.of("Asia/Taipei")).toLocalDate().toString(); }
    private List<String> keys(long user, String date, String kind) {
        String prefix = "meng:companion:";
        return List.of(prefix + "usage:" + user + ":" + date, prefix + "reserved:" + user + ":" + date + ":" + kind,
            prefix + "minute:" + user + ":" + kind);
    }
}

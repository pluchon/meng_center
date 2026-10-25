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

    private static final DefaultRedisScript<Long> RESERVE =
        new DefaultRedisScript<>(
            """
            redis.call('ZREMRANGEBYSCORE',KEYS[2],'-inf',ARGV[1]-120000)
            redis.call('ZREMRANGEBYSCORE',KEYS[3],'-inf',ARGV[1]-60000)
            local used=tonumber(redis.call('HGET',KEYS[1],ARGV[5]) or '0')
            if used+redis.call('ZCARD',KEYS[2]) >= tonumber(ARGV[3]) or redis.call('ZCARD',KEYS[3]) >= tonumber(ARGV[4]) then return 0 end
            redis.call('ZADD',KEYS[2],ARGV[1],ARGV[2])
            redis.call('ZADD',KEYS[3],ARGV[1],ARGV[2])
            redis.call('EXPIRE',KEYS[2],172800)
            redis.call('EXPIRE',KEYS[3],120)
            return 1
            """,
            Long.class
        );
    private static final DefaultRedisScript<Long> FINISH =
        new DefaultRedisScript<>(
            """
            if redis.call('ZREM',KEYS[2],ARGV[1]) == 0 then return 0 end
            if ARGV[3] == '1' then
              redis.call('HINCRBY',KEYS[1],ARGV[2],1)
              redis.call('EXPIRE',KEYS[1],172800)
            else redis.call('ZREM',KEYS[3],ARGV[1]) end
            return 1
            """,
            Long.class
        );
    private static final DefaultRedisScript<Long> UNLOCK =
        new DefaultRedisScript<>(
            """
            if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end
            return 0
            """,
            Long.class
        );
    private static final DefaultRedisScript<Long> PLAY =
        new DefaultRedisScript<>(
            """
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
            """,
            Long.class
        );

    public record Reservation(
        long user,
        String date,
        String token,
        List<String> kinds,
        boolean messageAllowed
    ) {}

    public String lock(long session) {
        String token = UUID.randomUUID().toString();
        if (
            !Boolean.TRUE.equals(
                redis
                    .opsForValue()
                    .setIfAbsent(
                        "meng:companion:lock:" + session,
                        token,
                        Duration.ofMinutes(2)
                    )
            )
        ) {
            throw new BusinessException(CompanionError.COMPANION_BUSY);
        }
        return token;
    }

    public String tryLock(long session) {
        String token = UUID.randomUUID().toString();
        return Boolean.TRUE.equals(
            redis
                .opsForValue()
                .setIfAbsent(
                    "meng:companion:lock:" + session,
                    token,
                    Duration.ofMinutes(2)
                )
        )
            ? token
            : null;
    }

    public boolean activate(long user, String token) {
        return Long.valueOf(1).equals(
            redis.execute(
                new DefaultRedisScript<>(
                    """
                    redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',ARGV[1]-120000)
                    if redis.call('ZCARD',KEYS[1])>=tonumber(ARGV[3]) then return 0 end
                    redis.call('ZADD',KEYS[1],ARGV[1],ARGV[2]) redis.call('EXPIRE',KEYS[1],180) return 1
                    """,
                    Long.class
                ),
                List.of("meng:companion:active:" + user),
                Long.toString(clock.millis()),
                token,
                Integer.toString(properties.getConcurrentTurns())
            )
        );
    }

    public void deactivate(long user, String token) {
        redis.opsForZSet().remove("meng:companion:active:" + user, token);
    }

    public void checkIp(long user, String ip, int length) {
        String hash = Integer.toHexString(ip == null ? 0 : ip.hashCode());
        String key =
            "meng:companion:ip:" +
            hash +
            ":" +
            clock.instant().getEpochSecond() / 60;
        Long count = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofMinutes(2));
        if (
            count != null && count > properties.getIpMinute()
        ) throw new BusinessException(CompanionError.COMPANION_COOLDOWN);
        String pattern = "meng:companion:pattern:" + user;
        redis.opsForList().leftPush(pattern, clock.millis() + ":" + length);
        redis.opsForList().trim(pattern, 0, 19);
        redis.expire(pattern, Duration.ofMinutes(30));
        var samples = redis.opsForList().range(pattern, 0, 19);
        if (abusive(samples)) {
            cooldown(user);
            throw new BusinessException(CompanionError.COMPANION_COOLDOWN);
        }
    }

    public static boolean abusive(List<String> samples) {
        if (samples == null || samples.size() < 20) return false;
        long min = Long.MAX_VALUE,
            max = 0,
            total = 0;
        boolean full = true;
        for (int i = 0; i < samples.size(); i++) {
            String[] current = samples.get(i).split(":");
            full &= Integer.parseInt(current[1]) >= 950;
            if (i == 0) continue;
            long gap = Math.abs(
                Long.parseLong(samples.get(i - 1).split(":")[0]) -
                    Long.parseLong(current[0])
            );
            min = Math.min(min, gap);
            max = Math.max(max, gap);
            total += gap;
        }
        return (full && total >= 600_000) || (min >= 1000 && max - min <= 200);
    }

    public boolean locked(long session, String token) {
        return token.equals(
            redis.opsForValue().get("meng:companion:lock:" + session)
        );
    }

    public void unlock(long session, String token) {
        redis.execute(UNLOCK, List.of("meng:companion:lock:" + session), token);
    }

    public void checkCooldown(long user) {
        if (
            Boolean.TRUE.equals(redis.hasKey("meng:companion:cooldown:" + user))
        ) throw new BusinessException(CompanionError.COMPANION_COOLDOWN);
    }

    public void cooldown(long user) {
        redis
            .opsForValue()
            .set(
                "meng:companion:cooldown:" + user,
                "1",
                Duration.ofMinutes(30)
            );
    }

    public Reservation reserve(long user) {
        return reserve(user, "Asia/Taipei");
    }

    public Reservation reserve(long user, String zone) {
        String date = date(zone);
        String token = UUID.randomUUID().toString();
        List<String> kinds = new ArrayList<>();
        try {
            for (String kind : List.of("message", "toy", "image", "web:0", "web:1", "web:2", "web:3", "webImage:0", "webImage:1")) {
                String base = kind.split(":")[0];
                int daily = switch (base) {
                    case "message" -> properties.getMessageDaily();
                    case "toy" -> properties.getToyDaily();
                    case "web" -> properties.getWebDaily();
                    case "webImage" -> properties.getWebImageDaily();
                    default -> properties.getImageDaily();
                };
                int minute = switch (base) {
                    case "message" -> properties.getMessageMinute();
                    case "toy" -> properties.getToyMinute();
                    case "web" -> properties.getWebMinute();
                    case "webImage" -> properties.getWebImageMinute();
                    default -> properties.getImageMinute();
                };
                var result = redis.execute(
                    RESERVE,
                    keys(user, date, kind),
                    Long.toString(clock.millis()),
                    reservationToken(token, kind),
                    Integer.toString(daily),
                    Integer.toString(minute),
                    base
                );
                if (Long.valueOf(1).equals(result)) kinds.add(kind);
                else if ("message".equals(kind)) break;
            }
            return new Reservation(
                user,
                date,
                token,
                List.copyOf(kinds),
                kinds.contains("message")
            );
        } catch (RuntimeException error) {
            finish(
                new Reservation(user, date, token, List.copyOf(kinds), false),
                List.of()
            );
            throw error;
        }
    }

    public void finish(Reservation r, List<String> used) {
        for (String kind : r.kinds())
            redis.execute(
                FINISH,
                keys(r.user(), r.date(), kind),
                reservationToken(r.token(),kind),
                kind.split(":")[0],
                used.contains(kind) ? "1" : "0"
            );
    }

    public List<String> tools(Reservation r) {
        var tools = new ArrayList<>(List.of("search_posts","read_post","my_recent_posts","my_growth","recall"));
        if (r.kinds().contains("toy")) tools.add("make_toy");
        if (r.kinds().contains("image")) tools.add("draw_picture");
        if (r.kinds().stream().anyMatch(k -> k.startsWith("web:"))) tools.addAll(List.of("web_search","read_link"));
        if (r.kinds().stream().anyMatch(k -> k.startsWith("webImage:"))) tools.add("search_images");
        return tools;
    }

    public Map<String,Long> toolLimits(Reservation r) {
        var counts = new java.util.HashMap<String,Long>();
        for(var kind:r.kinds()) counts.merge(kind.split(":")[0],1L,Long::sum);
        return counts;
    }

    private String reservationToken(String token,String kind) { return kind.contains(":")?token+":"+kind:token; }

    public void checkPoke(long user) {
        var result=redis.execute(new DefaultRedisScript<Long>(
            "redis.call('ZREMRANGEBYSCORE',KEYS[1],'-inf',ARGV[1]-60000); " +
            "if redis.call('ZCARD',KEYS[1])>=2 then return 0 end; " +
            "redis.call('ZADD',KEYS[1],ARGV[1],ARGV[2]);redis.call('EXPIRE',KEYS[1],120);return 1",Long.class),
            List.of("meng:companion:poke:"+user),Long.toString(clock.millis()),UUID.randomUUID().toString());
        if(!Long.valueOf(1).equals(result)) throw new BusinessException(CompanionError.COMPANION_COOLDOWN);
    }

    public boolean controlAvailable(long user,String zone,String kind) {
        return !Boolean.TRUE.equals(redis.hasKey("meng:companion:"+kind+":"+user+":"+date(zone)));
    }

    public boolean useControl(long user,String zone,String kind) {
        String key="meng:companion:"+kind+":"+user+":"+date(zone), token=UUID.randomUUID().toString();
        if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key,token,Duration.ofDays(2)))) return false;
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if(status!=STATUS_COMMITTED) redis.execute(UNLOCK,List.of(key),token);
                    }
                });
        return true;
    }


    public Map<String, Object> usage(long user) {
        return usage(user, "Asia/Taipei");
    }

    public Map<String, Object> usage(long user, String zone) {
        var counts = redis
            .opsForHash()
            .entries("meng:companion:usage:" + user + ":" + date(zone));
        long seconds = number(
            redis
                .opsForHash()
                .get(
                    "meng:companion:play:" + user + ":" + date(zone),
                    "seconds"
                )
        );
        Long cooldown = redis.getExpire("meng:companion:cooldown:" + user);
        return Map.of(
            "messages",
            Math.max(
                0,
                properties.getMessageDaily() - number(counts.get("message"))
            ),
            "toys",
            Math.max(
                0,
                properties.getToyDaily() - number(counts.get("toy"))
            ),
            "images",
            Math.max(
                0,
                properties.getImageDaily() - number(counts.get("image"))
            ),
            "chatSeconds",
            seconds,
            "dailyReminder",
            seconds >= 7200,
            "cooldownSeconds",
            cooldown == null ? 0L : Math.max(0L, cooldown)
        );
    }

    public boolean restReminder(long user) {
        return restReminder(user, "Asia/Taipei");
    }

    public boolean restReminder(long user, String zone) {
        var map = redis
            .opsForHash()
            .entries("meng:companion:play:" + user + ":" + date(zone));
        long gap = clock.instant().getEpochSecond() - number(map.get("last"));
        return (
            gap <= 600 &&
            number(map.get("reminded")) == 0 &&
            number(map.get("chain")) + Math.max(0, gap) >= 3600
        );
    }

    public void play(long user) {
        play(user, "Asia/Taipei");
    }

    public void play(long user, String zone) {
        redis.execute(
            PLAY,
            List.of("meng:companion:play:" + user + ":" + date(zone)),
            Long.toString(clock.instant().getEpochSecond())
        );
    }

    public void retainImage(Reservation r) {
        redis
            .opsForZSet()
            .add(
                keys(r.user(), r.date(), "image").get(1),
                r.token(),
                clock.millis() + 600_000
            );
    }

    private long number(Object value) {
        return value == null ? 0 : Long.parseLong(value.toString());
    }

    private String date() {
        return clock
            .instant()
            .atZone(ZoneId.of("Asia/Taipei"))
            .toLocalDate()
            .toString();
    }

    private String date(String zone) {
        return clock.instant().atZone(ZoneId.of(zone)).toLocalDate().toString();
    }

    private List<String> keys(long user, String date, String kind) {
        kind = kind.split(":")[0];
        String prefix = "meng:companion:";
        return List.of(
            prefix + "usage:" + user + ":" + date,
            prefix + "reserved:" + user + ":" + date + ":" + kind,
            prefix + "minute:" + user + ":" + kind
        );
    }
}

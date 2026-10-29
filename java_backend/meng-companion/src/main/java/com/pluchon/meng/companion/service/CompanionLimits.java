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
    public record Reservation(
        long user,
        String date,
        String token,
        List<String> kinds,
        boolean messageAllowed
    ) {
        public boolean searchAllowed() { return kinds.contains("search"); }
    }

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
        String date = date(zone), token = UUID.randomUUID().toString();
        var result = redis.execute(RESERVE,keys(user,date,"message"),Long.toString(clock.millis()),token,
            Integer.toString(properties.getMessageDaily()),Integer.toString(properties.getMessageMinute()),"message");
        boolean allowed = Long.valueOf(1).equals(result);
        var kinds = new ArrayList<String>();
        if (allowed) {
            kinds.add("message");
            Long search = redis.execute(RESERVE, keys(user,date,"search"),Long.toString(clock.millis()),
                token,Integer.toString(properties.getWebSearchDaily()),
                Integer.toString(properties.getWebSearchDaily()),"search");
            if (Long.valueOf(1).equals(search)) kinds.add("search");
        }
        return new Reservation(user,date,token,List.copyOf(kinds),allowed);
    }

    public int searchRemaining(Reservation reservation) {
        if (!reservation.searchAllowed()) return 0;
        Object value = redis.opsForHash().get(
            "meng:companion:usage:"+reservation.user()+":"+reservation.date(), "search");
        return Math.max(1, properties.getWebSearchDaily() - (int)number(value));
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

    private String reservationToken(String token,String kind) { return kind.contains(":")?token+":"+kind:token; }

    public Map<String,Object> usage(long user) { return usage(user,"Asia/Taipei"); }

    public Map<String,Object> usage(long user,String zone) {
        var used=redis.opsForHash().get("meng:companion:usage:"+user+":"+date(zone),"message");
        Long cooldown=redis.getExpire("meng:companion:cooldown:"+user);
        return Map.of("messages",Math.max(0,properties.getMessageDaily()-number(used)),
                      "cooldownSeconds",cooldown==null ? 0L : Math.max(0L,cooldown));
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

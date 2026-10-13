package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.security.AuthConstants;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.service.security.SessionService;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class DeviceService {

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private SessionService sessions;

    private final DefaultRedisScript<Long> legacy = new DefaultRedisScript<>(
        "if redis.call('HGET',KEYS[1],'userId')~=ARGV[1] then return 0 end redis.call('HSETNX',KEYS[1],'deviceId',ARGV[2]) redis.call('HSETNX',KEYS[1],'deviceName','未知设备') return 1",
        Long.class
    );
    private final DefaultRedisScript<Long> others = new DefaultRedisScript<>(
        "local n=0 for _,id in ipairs(redis.call('SMEMBERS',KEYS[1])) do if id~=ARGV[1] then redis.call('DEL',ARGV[2]..id) redis.call('SREM',KEYS[1],id) n=n+1 end end return n",
        Long.class
    );

    public record Device(
        String id,
        String name,
        String region,
        Instant loginAt,
        Instant lastActiveAt,
        boolean current
    ) {}

    private Instant time(Object value) {
        try {
            return value == null
                ? null
                : Instant.ofEpochSecond(Long.parseLong(value.toString()));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private Map<String, Map<Object, Object>> entries() {
        var identity = UserContext.require();
        var result = new HashMap<String, Map<Object, Object>>();
        String key = AuthConstants.USER_SESSIONS_PREFIX + identity.userId();
        Set<String> ids = redis.opsForSet().members(key);
        if (ids == null) return result;
        for (String id : ids) {
            String session = AuthConstants.SESSION_PREFIX + id;
            Long valid = redis.execute(
                legacy,
                List.of(session),
                Long.toString(identity.userId()),
                UUID.randomUUID().toString()
            );
            if (valid == null || valid != 1) {
                redis.opsForSet().remove(key, id);
                continue;
            }
            var values = redis.opsForHash().entries(session);
            if (
                Long.toString(identity.userId()).equals(values.get("userId")) &&
                values.get("deviceId") != null &&
                values.get("deviceName") != null
            ) result.put(id, values);
        }
        return result;
    }

    public List<Device> list() {
        String current = UserContext.require().sessionId();
        return entries()
            .entrySet()
            .stream()
            .map(e ->
                new Device(
                    e.getValue().get("deviceId").toString(),
                    e.getValue().get("deviceName").toString(),
                    Objects.toString(e.getValue().get("region"), null),
                    time(e.getValue().get("loginAt")),
                    time(e.getValue().get("lastActiveAt")),
                    e.getKey().equals(current)
                )
            )
            .sorted(
                Comparator.comparing(Device::current)
                    .reversed()
                    .thenComparing(
                        Device::lastActiveAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                    )
            )
            .toList();
    }

    public void revoke(String device) {
        var identity = UserContext.require();
        var entry = entries()
            .entrySet()
            .stream()
            .filter(e -> device.equals(e.getValue().get("deviceId")))
            .findFirst()
            .orElseThrow(() ->
                new BusinessException(CommonErrorCode.NOT_FOUND)
            );
        if (
            entry.getKey().equals(identity.sessionId())
        ) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        sessions.revoke(identity.userId(), entry.getKey());
    }

    public void revokeOthers() {
        var identity = UserContext.require();
        redis.execute(
            others,
            List.of(AuthConstants.USER_SESSIONS_PREFIX + identity.userId()),
            identity.sessionId(),
            AuthConstants.SESSION_PREFIX
        );
    }
}

package com.pluchon.meng.companion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pluchon.meng.companion.config.CompanionProperties;
import com.pluchon.meng.companion.service.CompanionLimits;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

// Redis 的缺失键及无过期时间不能让前端出现负数或永久倒计时。
class CompanionLimitsTest {
    @Test
    void usageReportsOnlyPositiveCooldownTtl() {
        var redis = mock(StringRedisTemplate.class, RETURNS_DEEP_STUBS);
        when(redis.opsForHash().entries(anyString())).thenReturn(Map.of());
        var service = new CompanionLimits();
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "clock", Clock.systemUTC());
        ReflectionTestUtils.setField(service, "properties", new CompanionProperties());
        for (Long ttl : new Long[] {1800L, 1L, 0L, -1L, -2L, null}) {
            when(redis.getExpire("meng:companion:cooldown:71")).thenReturn(ttl);
            assertEquals(ttl == null ? 0L : Math.max(0L, ttl), service.usage(71).get("cooldownSeconds"));
        }
    }
}

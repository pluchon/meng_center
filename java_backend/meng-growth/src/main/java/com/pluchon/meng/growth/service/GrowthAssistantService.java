package com.pluchon.meng.growth.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.error.ErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.growth.client.AiClient;
import com.pluchon.meng.growth.config.GrowthAiProperties;
import com.pluchon.meng.growth.config.GrowthProperties;
import com.pluchon.meng.growth.mapper.GrowthMapper;
import com.pluchon.meng.growth.mapper.GrowthPageMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 成长统计、总结缓存与模型计算协调
@Service
public class GrowthAssistantService {

    // 成长页面统计
    @Autowired
    private GrowthPageService pages;

    // 本域统计查询
    @Autowired
    private GrowthPageMapper mapper;

    // 成长账户和签到数据
    @Autowired
    private GrowthMapper growth;

    // 本域业务规则配置
    @Autowired
    private GrowthProperties properties;

    // 成长总结配额
    @Autowired
    private GrowthAiProperties quotas;

    // 纯计算 AI 客户端
    @Autowired
    private AiClient ai;

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private static final DefaultRedisScript<Long> TAKE = new DefaultRedisScript<>(
        "if tonumber(redis.call('GET',KEYS[1]) or '0')>=tonumber(ARGV[1]) or tonumber(redis.call('GET',KEYS[2]) or '0')>=tonumber(ARGV[2]) then return 0 end;redis.call('INCR',KEYS[1]);redis.call('EXPIRE',KEYS[1],ARGV[3]);redis.call('INCR',KEYS[2]);redis.call('EXPIRE',KEYS[2],120);return 1",
        Long.class
    );

    private enum AiError implements ErrorCode {
        UNAVAILABLE;

        public String code() {
            return "AI_UNAVAILABLE";
        }

        public String message() {
            return "AI 暂时没空";
        }

        public int httpStatus() {
            return 503;
        }
    }

    // 汇总本域成长数字并按统计摘要缓存总结
    public Map<String, Object> summarize(int days) {
        if (days != 7 && days != 30) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        long user = UserContext.require().userId();
        var now = clock.instant().atZone(ZoneId.of(properties.timezone()));
        var today = now.toLocalDate();
        var start = today.minusDays(days - 1L);
        var end = today.plusDays(1);
        var stats = pages.stats(days);
        var dates = growth.selectCheckInDates(user, start, today).stream().sorted().toList();
        int longest = 0,
            streak = 0;
        LocalDate previous = null;
        for (var date : dates) {
            streak = previous != null && previous.plusDays(1).equals(date) ? streak + 1 : 1;
            longest = Math.max(longest, streak);
            previous = date;
        }
        var account = growth.selectAccountForUpdate(user);
        long exp = account == null ? 0 : account.getExp();
        int level = 1;
        for (int i = 0; i < properties.thresholds().size(); i++) if (
            exp >= properties.thresholds().get(i)
        ) level = i + 1;
        long remaining =
            level < properties.thresholds().size()
                ? Math.max(0, properties.thresholds().get(level) - exp)
                : 0;
        var body = new LinkedHashMap<String, Object>();
        body.put("days", days);
        body.put("dailyExperience", stats.days());
        body.put("sources", mapper.selectSourceExperience(user, start, end));
        body.put("checkInDays", dates.size());
        body.put("longestStreak", longest);
        body.put("level", level);
        body.put("nextLevelExperience", remaining);
        String digest = org.springframework.util.DigestUtils.md5DigestAsHex(
            json.writeValueAsBytes(body)
        );
        String key = "growth:ai:summary:" + user + ":" + days;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            var node = json.readTree(cached);
            if (digest.equals(node.path("digest").asString())) return json.convertValue(
                node.path("response"),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
        }
        long ttl = Math.max(1, Duration.between(now, end.atStartOfDay(now.getZone())).toSeconds());
        String root = "growth:ai:quota:summary:" + user + ":";
        if (
            !Long.valueOf(1).equals(
                redis.execute(
                    TAKE,
                    List.of(root + today, root + "minute:" + clock.instant().getEpochSecond() / 60),
                    String.valueOf(quotas.daily()),
                    String.valueOf(quotas.minute()),
                    String.valueOf(ttl)
                )
            )
        ) throw new BusinessException(CommonErrorCode.RATE_LIMITED);
        Map<String, Object> result;
        try {
            result = ai.summarize(body);
        } catch (RuntimeException error) {
            throw new BusinessException(AiError.UNAVAILABLE);
        }
        var response = Map.<String, Object>of("stats", body, "result", result);
        redis
            .opsForValue()
            .set(
                key,
                json.writeValueAsString(Map.of("digest", digest, "response", response)),
                Duration.ofSeconds(ttl)
            );
        return response;
    }
}

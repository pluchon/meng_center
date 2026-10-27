package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.client.AiClient;
import com.pluchon.meng.content.config.AiProperties;
import com.pluchon.meng.content.controller.dto.AiDraftDTO;
import com.pluchon.meng.content.controller.vo.ImageVO;
import com.pluchon.meng.content.entity.enums.AiErrorCode;
import com.pluchon.meng.content.validation.PostRules;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 发帖辅助和基于本域统计的创作复盘
@Service
public class ContentAssistantService {

    // 纯计算 AI 客户端
    @Autowired
    private AiClient ai;

    // 用户辅助功能配额
    @Autowired
    private AiQuotaService quota;

    // 本域业务规则配置
    @Autowired
    private AiProperties properties;

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 配图归属与上传处理
    @Autowired
    private ImageService images;

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    // 校验非空草稿并按 Unicode 码点限制输入
    private Map<String, Object> draft(AiDraftDTO body) {
        if (body.title().isBlank() && body.content().isBlank()) throw new BusinessException(
            CommonErrorCode.INVALID_ARGUMENT
        );
        String text = body.content();
        int points = text.codePointCount(0, text.length());
        text = text.substring(0, text.offsetByCodePoints(0, Math.min(2000, points)));
        return Map.of("category", body.category().name(), "title", body.title(), "content", text);
    }

    // 结合热门候选生成并过滤标签建议
    public Map<String, Object> tags(AiDraftDTO body) {
        var input = new java.util.LinkedHashMap<>(draft(body));
        var candidates = jdbc.queryForList(
            "SELECT name FROM tag WHERE delete_state=0 ORDER BY use_count DESC,id LIMIT 50",
            String.class
        );
        input.put("candidates", candidates);
        quota.take(
            "tags",
            UserContext.require().userId(),
            properties.tagsDaily(),
            properties.tagsMinute()
        );
        var response = call(() -> ai.tags(input));
        var selected = new HashSet<>(
            (body.selectedTags() == null ? List.<String>of() : body.selectedTags())
                .stream()
                .map(PostRules::tagKey)
                .toList()
        );
        var result = new ArrayList<Map<String, Object>>();
        for (var item : (List<?>) response.get("tags")) {
            if (
                !(item instanceof Map<?, ?> tag) || !(tag.get("name") instanceof String name)
            ) continue;
            try {
                name = PostRules.tags(List.of(name)).get(0);
            } catch (RuntimeException invalid) {
                continue;
            }
            if (selected.add(PostRules.tagKey(name))) result.add(
                Map.of("name", name, "existing", candidates.contains(name))
            );
        }
        return Map.of("tags", result);
    }

    // 生成供用户选择的切入角度与开头
    public Map<String, Object> inspirations(AiDraftDTO body) {
        var input = draft(body);
        quota.take(
            "inspirations",
            UserContext.require().userId(),
            properties.inspirationsDaily(),
            properties.inspirationsMinute()
        );
        return call(() -> ai.inspirations(input));
    }

    // 生成封面并沿用普通图片入库处理
    public ImageVO cover(AiDraftDTO body) {
        var input = draft(body);
        quota.take(
            "covers",
            UserContext.require().userId(),
            properties.coversDaily(),
            properties.coversMinute()
        );
        byte[] bytes = call(() ->
            ai.covers(
                Map.of(
                    "title",
                    input.get("title"),
                    "summary",
                    input.get("content"),
                    "category",
                    input.get("category")
                )
            )
        );
        return images.uploadGenerated(bytes);
    }

    // 先统计本域创作数据，再复用或生成总结
    public Map<String, Object> review(int days) {
        if (days != 7 && days != 30) throw new BusinessException(CommonErrorCode.INVALID_ARGUMENT);
        long user = UserContext.require().userId();
        var now = clock.instant().atZone(ZoneId.of("Asia/Taipei"));
        var start = java.sql.Timestamp.from(
            now
                .toLocalDate()
                .minusDays(days - 1)
                .atStartOfDay(now.getZone())
                .toInstant()
        );
        var end = java.sql.Timestamp.from(
            now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()).toInstant()
        );
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("days", days);
        var daily = new java.util.HashMap<java.time.LocalDate, Map<String, Object>>();
        for (var row : jdbc.queryForList(
            "SELECT stat_date date,view_count viewCount,like_count likeCount,comment_count commentCount,favorite_count favoriteCount FROM author_daily_stat WHERE author_id=? AND stat_date>=? AND stat_date<? ORDER BY stat_date",
            user,
            now.toLocalDate().minusDays(days - 1),
            now.toLocalDate().plusDays(1)
        ))
            daily.put(java.time.LocalDate.parse(row.get("date").toString()), row);
        var dailyMetrics = new ArrayList<Map<String, Object>>();
        for (
            var date = now.toLocalDate().minusDays(days - 1);
            !date.isAfter(now.toLocalDate());
            date = date.plusDays(1)
        ) dailyMetrics.add(
            daily.getOrDefault(
                date,
                Map.of(
                    "date",
                    date,
                    "viewCount",
                    0L,
                    "likeCount",
                    0L,
                    "commentCount",
                    0L,
                    "favoriteCount",
                    0L
                )
            )
        );
        body.put("dailyMetrics", dailyMetrics);
        body.put(
            "postCount",
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM post WHERE author_id=? AND delete_state=0 AND create_time>=? AND create_time<?",
                Long.class,
                user,
                start,
                end
            )
        );
        String favoriteCounts =
            " LEFT JOIN (SELECT post_id,COUNT(DISTINCT user_id) favorites FROM post_favorite GROUP BY post_id) f ON f.post_id=p.id ";
        body.put(
            "bestPosts",
            jdbc.queryForList(
                "SELECT p.title,p.category,p.view_count viewCount,p.like_count likeCount,p.comment_count commentCount,COALESCE(f.favorites,0) favoriteCount FROM post p" +
                    favoriteCounts +
                    "WHERE p.author_id=? AND p.delete_state=0 AND p.create_time>=? AND p.create_time<? ORDER BY p.hot_score DESC,p.id DESC LIMIT 3",
                user,
                start,
                end
            )
        );
        body.put(
            "categories",
            jdbc.queryForList(
                "SELECT p.category,AVG(p.view_count) views,AVG(p.like_count) likes,AVG(p.comment_count) comments,AVG(COALESCE(f.favorites,0)) favorites FROM post p" +
                    favoriteCounts +
                    "WHERE p.author_id=? AND p.delete_state=0 AND p.create_time>=? AND p.create_time<? GROUP BY p.category",
                user,
                start,
                end
            )
        );
        body.put(
            "postingHours",
            jdbc.queryForList(
                "SELECT HOUR(DATE_ADD(create_time,INTERVAL 8 HOUR)) hour,COUNT(*) count FROM post WHERE author_id=? AND delete_state=0 AND create_time>=? AND create_time<? GROUP BY hour",
                user,
                start,
                end
            )
        );
        body.put("writtenTopics",writtenTopics(user,start,end));
        body.put("interestTopics",interestTopics(user));
        String key = "content:ai:creator-review:" + user + ":" + days;
        String digest = org.springframework.util.DigestUtils.md5DigestAsHex(
            json.writeValueAsBytes(body)
        );
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            var entry = json.readTree(cached);
            if (digest.equals(entry.path("digest").asString())) return json.convertValue(
                entry.path("result"),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
        }
        quota.take("creator", user, properties.reviewsDaily(), properties.reviewsMinute());
        var result = call(() -> ai.review(body));
        var response = Map.<String, Object>of("stats", body, "result", result);
        redis
            .opsForValue()
            .set(
                key,
                json.writeValueAsString(Map.of("digest", digest, "result", response)),
                Duration.between(now, end.toInstant().atZone(now.getZone()))
            );
        return response;
    }

    // 从作者本人近期帖子的主题中统计方向，每篇帖子对同一主题只计一次。
    private List<String> writtenTopics(long user,java.sql.Timestamp start,java.sql.Timestamp end) {
        var counts=new java.util.HashMap<String,Long>();
        for (var row:jdbc.queryForList(
            "SELECT f.topics FROM post_ai_feature f JOIN post p ON p.id=f.post_id WHERE p.author_id=? AND p.delete_state=0 AND f.delete_state=0 AND p.create_time>=? AND p.create_time<? ORDER BY p.create_time DESC,p.id DESC",
            user,start,end)) {
            var unique=new java.util.HashSet<String>();
            var topics=json.readTree(row.get("topics").toString());
            if (topics.isArray()) for (var topic:topics) if (topic.isString() && !topic.asString().isBlank())
                unique.add(topic.asString());
            unique.forEach(topic -> counts.merge(topic,1L,Long::sum));
        }
        return counts.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()
            .thenComparing(Map.Entry.comparingByKey())).limit(3).map(Map.Entry::getKey).toList();
    }

    // 画像只提供主题名称，不把权重或其他用户资料交给模型。
    private List<String> interestTopics(long user) {
        return redis.opsForHash().entries("content:rec:profile:"+user).entrySet().stream()
            .filter(e -> e.getKey().toString().startsWith("t:") && e.getValue() instanceof String)
            .filter(e -> e.getValue().toString().matches("[0-9]+(?:\\.[0-9]+)?"))
            .filter(e -> Double.parseDouble(e.getValue().toString())>0)
            .sorted(java.util.Comparator.<Map.Entry<Object,Object>>comparingDouble(
                e -> Double.parseDouble(e.getValue().toString())).reversed()
                .thenComparing(e -> e.getKey().toString()))
            .limit(3).map(e -> e.getKey().toString().substring(2)).toList();
    }

    // 统一模型拒绝、超时与不可用语义
    private <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (feign.FeignException error) {
            throw new BusinessException(
                error.status() == 422 ? AiErrorCode.REJECTED : AiErrorCode.UNAVAILABLE
            );
        } catch (RuntimeException error) {
            throw new BusinessException(AiErrorCode.UNAVAILABLE);
        }
    }
}

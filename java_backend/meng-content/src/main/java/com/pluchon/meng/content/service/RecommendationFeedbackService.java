package com.pluchon.meng.content.service;

import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.config.AiProperties;
import com.pluchon.meng.content.mapper.PostMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

// 不感兴趣反馈与兴趣扣减
@Service
public class RecommendationFeedbackService {

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 帖子数据与内容版本
    @Autowired
    private PostMapper posts;

    // 帖子向量读写与召回
    @Autowired
    private PostVectorIndex vectors;

    // 用户辅助功能配额
    @Autowired
    private AiQuotaService quota;

    // 本域业务规则配置
    @Autowired
    private AiProperties properties;

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    // 幂等记录反馈，提交后扣减兴趣并更新快照
    @Transactional(rollbackFor = Exception.class)
    public void feedback(long id) {
        long user = UserContext.require().userId();
        var post = posts.selectById(id);
        if (post == null) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        if (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommend_feedback WHERE user_id=? AND post_id=?",
                Long.class,
                user,
                id
            ) > 0
        ) return;
        quota.take("feedback", user, properties.feedbackDaily(), properties.feedbackMinute());
        if (
            jdbc.update(
                "INSERT IGNORE INTO recommend_feedback(user_id,post_id,author_id) VALUES (?,?,?)",
                user,
                id,
                post.getAuthorId()
            ) == 0
        ) return;
        jdbc.update("UPDATE recommend_log SET clicked=0 WHERE user_id=? AND post_id=?", user, id);
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM recommend_feedback WHERE user_id=? AND author_id=? AND create_time>=?",
            Integer.class,
            user,
            post.getAuthorId(),
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(7)))
        );
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                public void afterCommit() {
                    try {
                        redis
                            .opsForZSet()
                            .add(
                                "content:rec:seen:" + user,
                                String.valueOf(id),
                                clock.instant().getEpochSecond()
                            );
                        redis.expire("content:rec:seen:" + user, Duration.ofDays(30));
                        var fields = new ArrayList<>(List.of("c:" + post.getCategory().name()));
                        try {
                            var doc = vectors.get(id);
                            if (doc != null) doc.topics().forEach(t -> fields.add("t:" + t));
                        } catch (Exception ignored) {}
                        String profile = "content:rec:profile:" + user;
                        var subtract =
                            new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
                                "for i=1,#ARGV do local v=tonumber(redis.call('HGET',KEYS[1],ARGV[i]) or '0');redis.call('HSET',KEYS[1],ARGV[i],math.max(0,v-5)) end;return 1",
                                Long.class
                            );
                        redis.execute(subtract, List.of(profile), fields.toArray());
                        if (count >= 3) {
                            redis
                                .opsForHash()
                                .put(
                                    "content:rec:mute-author:" + user,
                                    String.valueOf(post.getAuthorId()),
                                    String.valueOf(
                                        clock.instant().plus(Duration.ofDays(7)).getEpochSecond()
                                    )
                                );
                            redis.expire("content:rec:mute-author:" + user, Duration.ofDays(7));
                        }
                        for (String category : List.of(
                            "ALL",
                            "ANIME",
                            "CREATION",
                            "GAME",
                            "TECH",
                            "DAILY"
                        )) {
                            String key = "content:rec:feed:" + user + ":" + category;
                            String cached = redis.opsForValue().get(key);
                            if (cached == null) continue;
                            var entries = json.readValue(
                                cached,
                                new tools.jackson.core.type.TypeReference<
                                    List<RecommendationService.Entry>
                                >() {}
                            );
                            var ttl = redis.getExpire(key);
                            redis.opsForValue().set(
                                key,
                                json.writeValueAsString(
                                    entries
                                        .stream()
                                        .filter(e -> e.id() != id)
                                        .toList()
                                ),
                                Duration.ofSeconds(Math.max(1, ttl == null ? 1 : ttl))
                            );
                        }
                    } catch (RuntimeException error) {
                        org.slf4j.LoggerFactory.getLogger(RecommendationFeedbackService.class).warn(
                            "Feedback profile update deferred"
                        );
                    }
                }
            }
        );
    }
}

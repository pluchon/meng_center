package com.pluchon.meng.content.service;

import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.ContentInteractionEvent;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.PostMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;

// 用户兴趣权重、兴趣向量与行为记录
@Service
public class RecommendationProfileService {

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 帖子数据与内容版本
    @Autowired
    private PostMapper posts;

    // 帖子向量读写与召回
    @Autowired
    private PostVectorIndex vectors;

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 有界异步执行器
    @Autowired
    @Qualifier("recommendationExecutor")
    private ThreadPoolTaskExecutor executor;

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(
        RecommendationProfileService.class
    );

    // 异步记录浏览兴趣与已读集合
    public void viewed(long user, long post) {
        async(() -> record(user, post, 1, true));
    }

    // 事务提交后记录喜欢与收藏兴趣
    @TransactionalEventListener
    public void interacted(ContentInteractionEvent event) {
        int weight = switch (event.eventType()) {
            case "post.liked" -> 4;
            case "post.favorited" -> 5;
            default -> 0;
        };
        if (weight > 0) async(() -> record(event.actorId(), event.postId(), weight, false));
    }

    // 事务提交后记录评论兴趣
    @TransactionalEventListener
    public void commented(CommentCreatedEvent event) {
        async(() -> record(event.authorId(), event.postId(), 3, false));
    }

    // 内容变化后失效相关缓存或清理向量
    @TransactionalEventListener
    public void changed(PostReadModelEvent event) {
        if (event.kind() == PostReadModelEvent.Kind.DELETED) async(() -> {
            try {
                vectors.delete(event.id());
                vectors.completed(event.id());
            } catch (Exception error) {
                LOG.warn("Vector delete deferred post={}", event.id());
            }
            redis.delete("content:related:" + event.id());
        });
        if (event.kind() == PostReadModelEvent.Kind.UPDATED) async(() ->
            redis.delete("content:related:" + event.id())
        );
    }

    // 提交有界任务并隔离派生数据失败
    private void async(Runnable action) {
        try {
            executor.execute(() -> {
                try {
                    action.run();
                } catch (RuntimeException error) {
                    LOG.warn(
                        "Recommendation profile deferred error={}",
                        error.getClass().getSimpleName()
                    );
                }
            });
        } catch (org.springframework.core.task.TaskRejectedException ignored) {
            LOG.warn("Recommendation profile deferred to rebuild");
        }
    }

    // 累计兴趣权重并维护已读上限
    private void record(long user, long id, int weight, boolean view) {
        var post = posts.selectById(id);
        if (post == null) return;
        String root = "content:rec:";
        var profile = redis.opsForHash();
        profile.increment(root + "profile:" + user, "c:" + post.getCategory().name(), weight);
        profile.increment(root + "profile:" + user, "_count", 1);
        try {
            var doc = vectors.get(id);
            if (doc != null) for (String topic : doc.topics())
                profile.increment(root + "profile:" + user, "t:" + topic, weight);
        } catch (Exception ignored) {}
        redis.expire(root + "profile:" + user, Duration.ofDays(60));
        if (view) {
            String seen = root + "seen:" + user;
            redis.opsForZSet().add(seen, String.valueOf(id), clock.instant().getEpochSecond());
            redis
                .opsForZSet()
                .removeRangeByScore(
                    seen,
                    0,
                    clock.instant().minus(Duration.ofDays(30)).getEpochSecond()
                );
            Long n = redis.opsForZSet().zCard(seen);
            if (n != null && n > 1000) redis.opsForZSet().removeRange(seen, 0, n - 1001);
            redis.expire(seen, Duration.ofDays(30));
        } else rebuildVector(user);
    }

    // 只标记最近曝光，负反馈样本不改为点击
    public void clicked(long user, long post) {
        jdbc.update(
            "UPDATE recommend_log SET clicked=1 WHERE user_id=? AND post_id=? AND create_time>=? AND NOT EXISTS (SELECT 1 FROM recommend_feedback f WHERE f.user_id=? AND f.post_id=?) ORDER BY id DESC LIMIT 1",
            user,
            post,
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofMinutes(30))),
            user,
            post
        );
    }

    public record Interaction(long post, int weight, LocalDateTime time, long count) {
        public Interaction(long post, int weight, LocalDateTime time) {
            this(post, weight, time, 1);
        }
    }

    // 读取最近互动用于兴趣向量计算
    public List<Interaction> interactions(long user) {
        return jdbc.query(
            "SELECT post_id,weight,at_time FROM (SELECT post_id,4 weight,create_time at_time FROM post_like WHERE user_id=? UNION ALL SELECT post_id,5,create_time FROM post_favorite WHERE user_id=? UNION ALL SELECT post_id,3,create_time FROM comment WHERE author_id=? AND delete_state=0) a WHERE at_time>=? ORDER BY at_time DESC LIMIT 50",
            (rs, n) ->
                new Interaction(rs.getLong(1), rs.getInt(2), rs.getTimestamp(3).toLocalDateTime()),
            user,
            user,
            user,
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30)))
        );
    }

    // 按行为与时间衰减计算归一化兴趣向量
    public void rebuildVector(long user) {
        var history = interactions(user);
        Map<Long, PostVectorIndex.Document> docs;
        try {
            docs = vectors.batch(history.stream().map(Interaction::post).distinct().toList());
        } catch (Exception error) {
            return;
        }
        double[] sum = new double[1024];
        for (var item : history) {
            var doc = docs.get(item.post());
            if (doc == null || "BLOCK".equals(doc.verdict())) continue;
            double weight =
                item.weight() *
                Math.pow(
                    .5,
                    Duration.between(
                        item.time().toInstant(ZoneOffset.UTC),
                        clock.instant()
                    ).toSeconds() /
                        86400d /
                        7
                );
            for (int i = 0; i < sum.length; i++) sum[i] += doc.vector().get(i) * weight;
        }
        double norm = Math.sqrt(
            Arrays.stream(sum)
                .map(v -> v * v)
                .sum()
        );
        if (norm == 0) {
            redis.delete("content:rec:vec:" + user);
            return;
        }
        var bytes = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        for (double value : sum) bytes.putFloat((float) (value / norm));
        redis
            .opsForValue()
            .set(
                "content:rec:vec:" + user,
                Base64.getEncoder().encodeToString(bytes.array()),
                Duration.ofDays(60)
            );
        redis
            .opsForValue()
            .set(
                "content:rec:vec-version:" + user,
                Objects.toString(
                    redis.opsForValue().get("content:vector:active"),
                    "meng_vector_post_blue"
                ),
                Duration.ofDays(60)
            );
    }

    // 从本域持久行为恢复活跃用户画像
    public void rebuild() {
        var since = java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(7)));
        var active = jdbc.queryForList(
            "SELECT user_id FROM recommend_log WHERE create_time>=? UNION SELECT user_id FROM post_like WHERE create_time>=? UNION SELECT user_id FROM post_favorite WHERE create_time>=? UNION SELECT author_id FROM comment WHERE create_time>=? AND delete_state=0",
            Long.class,
            since,
            since,
            since,
            since
        );
        for (long user : active) {
            var history = jdbc.query(
                "SELECT post_id,SUM(weight) weight,MIN(at_time) at_time,COUNT(*) n FROM (SELECT post_id,4 weight,create_time at_time FROM post_like WHERE user_id=? UNION ALL SELECT post_id,5,create_time FROM post_favorite WHERE user_id=? UNION ALL SELECT post_id,3,create_time FROM comment WHERE author_id=? AND delete_state=0) a WHERE at_time>=? GROUP BY post_id,DATE(at_time)",
                (rs, n) ->
                    new Interaction(
                        rs.getLong(1),
                        rs.getInt(2),
                        rs.getTimestamp(3).toLocalDateTime(),
                        rs.getLong(4)
                    ),
                user,
                user,
                user,
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30)))
            );
            var values = new HashMap<String, String>();
            var ids = history.stream().map(Interaction::post).distinct().toList();
            var byId = new HashMap<Long, com.pluchon.meng.content.entity.PostEntity>();
            if (!ids.isEmpty()) posts.selectByIds(ids).forEach(p -> byId.put(p.getId(), p));
            Map<Long, PostVectorIndex.Document> docs;
            try {
                docs = vectors.batch(ids);
            } catch (Exception error) {
                docs = Map.of();
            }
            var totals = new HashMap<String, Double>();
            for (var item : history) {
                var post = byId.get(item.post());
                if (post == null) continue;
                double weight =
                    item.weight() *
                    Math.pow(
                        .9,
                        Duration.between(
                            item.time().toInstant(ZoneOffset.UTC),
                            clock.instant()
                        ).toDays()
                    );
                totals.merge("c:" + post.getCategory().name(), weight, Double::sum);
                var doc = docs.get(item.post());
                if (doc != null) for (String topic : doc.topics())
                    totals.merge("t:" + topic, weight, Double::sum);
            }
            var feedback = jdbc.queryForList(
                "SELECT post_id FROM recommend_feedback WHERE user_id=? AND create_time>=?",
                Long.class,
                user,
                java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(30)))
            );
            Map<Long, PostVectorIndex.Document> feedbackDocs;
            try {
                feedbackDocs = vectors.batch(feedback);
            } catch (Exception error) {
                feedbackDocs = Map.of();
            }
            for (long post : feedback) {
                var p = posts.selectById(post);
                if (p == null) continue;
                totals.computeIfPresent("c:" + p.getCategory().name(), (k, v) ->
                    Math.max(0, v - 5)
                );
                var document = feedbackDocs.get(post);
                if (document != null) for (String topic : document.topics())
                    totals.computeIfPresent("t:" + topic, (k, v) -> Math.max(0, v - 5));
            }
            totals.forEach((k, v) -> {
                if (v >= .1) values.put(k, String.valueOf(v));
            });
            values.put(
                "_count",
                String.valueOf(history.stream().mapToLong(Interaction::count).sum())
            );
            String key = "content:rec:profile:" + user;
            redis.delete(key);
            redis.opsForHash().putAll(key, values);
            redis.expire(key, Duration.ofDays(60));
            rebuildVector(user);
        }
    }
}

package com.pluchon.meng.content.service;

import com.pluchon.meng.content.config.ContentProperties;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.PostMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

// 热门帖子排行的蓝绿维护与增量更新
@Service
public class HotRankService {

    // 运行日志
    private static final Logger LOG = LoggerFactory.getLogger(HotRankService.class);

    // 缓存键前缀
    private static final String ROOT = "meng:content:hot:";

    // 仅允许锁持有者释放锁
    private static final DefaultRedisScript<Long> UNLOCK =
            new DefaultRedisScript<>(
                    "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1])"
                            + " end return 0",
                    Long.class);

    // 仅更新排行中已存在成员的热度
    private static final DefaultRedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(
                    "if redis.call('ZSCORE',KEYS[1],ARGV[1]) then"
                        + " redis.call('ZADD',KEYS[1],'XX','INCR',ARGV[2],ARGV[1]) end return 1",
                    Long.class);

    // 协调排行增量写入与批次切换
    private final Object rankWrites = new Object();

    // 缓存和操作计数
    @Autowired
    private StringRedisTemplate redis;

    // 帖子数据存取
    @Autowired
    private PostMapper posts;

    // 当前业务域配置
    @Autowired
    private ContentProperties properties;

    // 是否已有热榜重建任务排队
    private final java.util.concurrent.atomic.AtomicBoolean queued =
            new java.util.concurrent.atomic.AtomicBoolean();

    // 业务时钟
    @Autowired
    private Clock clock;

    // 有界维护线程池
    @Autowired
    @Qualifier("contentMaintenanceExecutor")
    private ThreadPoolTaskExecutor executor;

    // 排行页主键和总数
    public record RankPage(
            // 当前页帖子编号
            List<Long> ids,
            // 命中记录总数
            long total) {}

    // 读取热门排行页，不可用时交由数据库降级
    public RankPage read(PostCategory category, long page, long size) {
        try {
            var active = redis.opsForValue().get(ROOT + "active");
            if (active == null) {
                requestRebuild();
                return null;
            }
            var key = key(active, category);
            if (!Boolean.TRUE.equals(redis.hasKey(key))) {
                requestRebuild();
                return null;
            }
            var rows = redis.opsForZSet().reverseRange(key, (page - 1) * size, page * size - 1);
            var total = redis.opsForZSet().zCard(key);
            return new RankPage(
                    rows == null ? List.of() : rows.stream().map(Long::parseLong).toList(),
                    total == null ? 0 : total);
        } catch (RuntimeException e) {
            LOG.warn("Hot rank read unavailable");
            return null;
        }
    }

    // 服务启动后请求异步重建热榜
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        requestRebuild();
    }

    // 合并重复请求并提交热榜重建任务
    public void requestRebuild() {
        if (!queued.compareAndSet(false, true)) return;
        try {
            executor.execute(
                    () -> {
                        try {
                            rebuild();
                        } finally {
                            queued.set(false);
                        }
                    });
        } catch (RuntimeException e) {
            queued.set(false);
            LOG.warn("Hot rank rebuild queue full");
        }
    }

    // 分批重算热度并切换生效排行
    public boolean rebuild() {
        String token = UUID.randomUUID().toString();
        String lock = ROOT + "lock";
        String next = null;
        boolean locked = false;
        try {
            locked =
                    Boolean.TRUE.equals(
                            redis.opsForValue().setIfAbsent(lock, token, Duration.ofMinutes(10)));
            if (!locked) return true;
            String old = redis.opsForValue().get(ROOT + "active");
            next = "blue".equals(old) ? "green" : "blue";
            clear(next);
            redis.opsForValue().set(ROOT + "building", next, Duration.ofMinutes(10));
            var since =
                    LocalDateTime.ofInstant(
                            clock.instant().minus(Duration.ofDays(7)), ZoneOffset.UTC);
            posts.recalculateRecentHotScores(
                    since,
                    properties.viewWeight(),
                    properties.likeWeight(),
                    properties.commentWeight(),
                    properties.favoriteWeight());
            long cursor = 0;
            while (true) {
                synchronized (rankWrites) {
                    var rows = posts.selectRebuildBatch(cursor, since);
                    if (rows.isEmpty()) break;
                    var all = new HashSet<ZSetOperations.TypedTuple<String>>();
                    for (var p : rows)
                        all.add(
                                ZSetOperations.TypedTuple.of(
                                        member(p.getId()), p.getHotScore().doubleValue()));
                    redis.opsForZSet().add(key(next, null), all);
                    for (var category : PostCategory.values()) {
                        var group = new HashSet<ZSetOperations.TypedTuple<String>>();
                        for (var p : rows)
                            if (p.getCategory() == category)
                                group.add(
                                        ZSetOperations.TypedTuple.of(
                                                member(p.getId()), p.getHotScore().doubleValue()));
                        if (!group.isEmpty()) redis.opsForZSet().add(key(next, category), group);
                    }
                    cursor = rows.get(rows.size() - 1).getId();
                }
            }
            synchronized (rankWrites) {
                if (!token.equals(redis.opsForValue().get(lock)))
                    throw new IllegalStateException("Rebuild lease expired");
                redis.opsForValue().set(ROOT + "active", next);
                redis.delete(ROOT + "building");
                if (old != null) clear(old);
            }
            return true;
        } catch (RuntimeException e) {
            LOG.warn("Hot rank rebuild failed");
            return false;
        } finally {
            if (locked)
                try {
                    redis.execute(UNLOCK, List.of(lock), token);
                } catch (RuntimeException e) {
                    LOG.warn("Hot rank unlock failed");
                }
        }
    }

    // 事务提交后增量更新当前和构建中的排行
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(PostReadModelEvent event) {
        synchronized (rankWrites) {
            try {
                var sets = new HashSet<String>();
                String active = redis.opsForValue().get(ROOT + "active");
                String building = redis.opsForValue().get(ROOT + "building");
                if (active != null) sets.add(active);
                if (building != null) sets.add(building);
                var post =
                        event.kind() == PostReadModelEvent.Kind.DELETED
                                ? null
                                : posts.selectById(event.id());
                for (var set : sets) {
                    String value = member(event.id());
                    if (post == null) {
                        redis.opsForZSet().remove(key(set, null), value);
                        redis.opsForZSet().remove(key(set, event.category()), value);
                        continue;
                    }
                    if (event.kind() == PostReadModelEvent.Kind.SCORE) {
                        redis.execute(
                                INCREMENT,
                                List.of(key(set, null)),
                                value,
                                Long.toString(event.delta()));
                        redis.execute(
                                INCREMENT,
                                List.of(key(set, post.getCategory())),
                                value,
                                Long.toString(event.delta()));
                    } else if (event.kind() == PostReadModelEvent.Kind.PUBLISHED) {
                        redis.opsForZSet().add(key(set, null), value, post.getHotScore());
                        redis.opsForZSet()
                                .add(key(set, post.getCategory()), value, post.getHotScore());
                    } else if (event.category() != post.getCategory()) {
                        redis.opsForZSet().remove(key(set, event.category()), value);
                        if (redis.opsForZSet().score(key(set, null), value) != null)
                            redis.opsForZSet()
                                    .add(key(set, post.getCategory()), value, post.getHotScore());
                    }
                }
            } catch (RuntimeException e) {
                LOG.warn("Hot rank update failed for post {}", event.id());
            }
        }
    }

    // 清除指定色标的排行集合
    private void clear(String set) {
        var keys = new ArrayList<String>();
        keys.add(key(set, null));
        for (var c : PostCategory.values()) keys.add(key(set, c));
        redis.delete(keys);
    }

    // 组装分类排行缓存键
    private static String key(String set, PostCategory category) {
        return ROOT + set + ":" + (category == null ? "ALL" : category.name());
    }

    // 补齐主键长度以保证同分帖子排序稳定
    public static String member(long id) {
        String value = Long.toString(id);
        return "0".repeat(19 - value.length()) + value;
    }
}

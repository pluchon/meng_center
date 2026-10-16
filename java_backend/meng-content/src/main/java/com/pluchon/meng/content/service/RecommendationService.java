package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.client.UserClient;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.controller.vo.PostFeedVO.RecommendationReason;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.mapper.PostMapper;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 并行召回、排序打散与稳定分页快照
@Service
public class RecommendationService {

    // 帖子数据与内容版本
    @Autowired
    private PostMapper posts;

    // 帖子展示数据批量组装
    @Autowired
    private PostService feed;

    // 帖子向量读写与召回
    @Autowired
    private PostVectorIndex vectors;

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 公开用户与关注关系客户端
    @Autowired
    private UserClient users;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 兴趣画像或资料审核业务
    @Autowired
    private RecommendationProfileService profiles;

    // 有界异步执行器
    @Autowired
    @Qualifier("recommendationExecutor")
    private ThreadPoolTaskExecutor executor;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public record Entry(
        long id,
        String route,
        RecommendationReason reason,
        List<Double> features
    ) {}

    // 读取稳定快照并记录本页曝光
    public PageResult<PostFeedVO> page(PostCategory category, long page, long size) {
        long user = UserContext.require().userId();
        try {
            String key = key(user, category),
                cached = page == 1 ? null : redis.opsForValue().get(key);
            List<Entry> entries;
            if (cached == null) {
                entries = rank(user, category);
                redis
                    .opsForValue()
                    .set(key, json.writeValueAsString(entries), Duration.ofMinutes(30));
            } else entries = json.readValue(
                cached,
                new tools.jackson.core.type.TypeReference<List<Entry>>() {}
            );
            int start = (int) Math.min(entries.size(), Math.multiplyExact(page - 1, size));
            var selected = entries.subList(start, (int) Math.min(entries.size(), start + size));
            var rows = new HashMap<Long, PostEntity>();
            if (!selected.isEmpty()) posts
                .selectByIds(selected.stream().map(Entry::id).toList())
                .stream()
                .filter(p -> "NORMAL".equals(p.getReviewState().name()))
                .forEach(p -> rows.put(p.getId(), p));
            var ordered = selected
                .stream()
                .map(e -> rows.get(e.id()))
                .filter(Objects::nonNull)
                .toList();
            var reasons = new HashMap<Long, Entry>();
            selected.forEach(e -> reasons.put(e.id(), e));
            var result = feed
                .hydrate(ordered)
                .stream()
                .map(p -> {
                    var entry = reasons.get(p.id());
                    var reason = entry.reason();
                    if ("FOLLOW".equals(reason.type())) reason = new RecommendationReason(
                        "FOLLOW",
                        p.author().nickname(),
                        null,
                        null
                    );
                    return p.recommended(reason);
                })
                .toList();
            for (int i = 0; i < selected.size(); i++) {
                var e = selected.get(i);
                if (!rows.containsKey(e.id())) continue;
                var features = new ArrayList<>(e.features());
                features.add((double) (start + i + 1));
                jdbc.update(
                    "INSERT INTO recommend_log(user_id,post_id,route,position,features) VALUES (?,?,?,?,?)",
                    user,
                    e.id(),
                    e.route(),
                    start + i + 1,
                    json.writeValueAsString(features)
                );
            }
            return new PageResult<>(result, entries.size(), page, size);
        } catch (RuntimeException unavailable) {
            return feed.feed(category, PostSort.HOT, page, size);
        }
    }

    // 按用户与分类隔离推荐快照
    private String key(long user, PostCategory category) {
        return "content:rec:feed:" + user + ":" + (category == null ? "ALL" : category.name());
    }

    // 限定时间窗批量读取候选帖子
    private List<PostEntity> recent(PostCategory category, int days, int limit, boolean hot) {
        var query = Wrappers.<PostEntity>lambdaQuery()
            .eq(PostEntity::getReviewState, "NORMAL")
            .eq(category != null, PostEntity::getCategory, category)
            .ge(
                PostEntity::getCreateTime,
                LocalDateTime.ofInstant(
                    clock.instant().minus(Duration.ofDays(days)),
                    ZoneOffset.UTC
                )
            );
        if (hot) query.orderByDesc(PostEntity::getHotScore);
        query.orderByDesc(PostEntity::getId).last("LIMIT " + limit);
        return posts.selectList(query);
    }

    // 并行召回后统一打分和贪心打散
    private List<Entry> rank(long user, PostCategory category) {
        var profile = new HashMap<String, Double>();
        redis
            .opsForHash()
            .entries("content:rec:profile:" + user)
            .forEach((k, v) -> profile.put(k.toString(), Double.parseDouble(v.toString())));
        boolean cold = profile.getOrDefault("_count", 0d) < 5;
        Set<Long> following;
        try {
            following = new HashSet<>(users.followees(user).data());
        } catch (RuntimeException error) {
            following = Set.of();
        }
        final var followees = following;
        // 近 7 天的帖子太少（社区冷清）时放宽到近 90 天，老帖按新鲜度排在后面，不至于永远没人看到
        var popular = CompletableFuture.supplyAsync(() -> {
            var week = recent(category, 7, 200, true);
            return week.size() >= 60 ? week : recent(category, 90, 200, true);
        }, executor);
        var followed = CompletableFuture.supplyAsync(() -> {
            if (followees.isEmpty()) return List.<PostEntity>of();
            return posts.selectList(
                Wrappers.<PostEntity>lambdaQuery()
                    .in(PostEntity::getAuthorId, followees)
                    .eq(PostEntity::getReviewState, "NORMAL")
                    .eq(category != null, PostEntity::getCategory, category)
                    .ge(
                        PostEntity::getCreateTime,
                        LocalDateTime.ofInstant(
                            clock.instant().minus(Duration.ofDays(7)),
                            ZoneOffset.UTC
                        )
                    )
                    .orderByDesc(PostEntity::getId)
                    .last("LIMIT 200")
            );
        }, executor);
        var interests = CompletableFuture.supplyAsync(() -> {
            try {
                return cold
                    ? List.<PostVectorIndex.Document>of()
                    : vectors.byTopics(profile, category);
            } catch (Exception error) {
                return List.<PostVectorIndex.Document>of();
            }
        }, executor);
        var nearest = CompletableFuture.supplyAsync(() -> {
            try {
                var vector = readVector(user);
                return cold || vector.isEmpty()
                    ? List.<PostVectorIndex.Hit>of()
                    : vectors.nearest(vector, category, 30, 200);
            } catch (Exception error) {
                return List.<PostVectorIndex.Hit>of();
            }
        }, executor);
        var routes = new LinkedHashMap<Long, Set<String>>();
        var byId = new HashMap<Long, PostEntity>();
        for (var post : popular.join()) {
            byId.put(post.getId(), post);
            routes.computeIfAbsent(post.getId(), x -> new HashSet<>()).add("HOT");
        }
        for (var post : followed.join()) {
            byId.put(post.getId(), post);
            routes.computeIfAbsent(post.getId(), x -> new HashSet<>()).add("FOLLOW");
        }
        var documents = new HashMap<Long, PostVectorIndex.Document>();
        var similarities = new HashMap<Long, Double>();
        for (var doc : interests.join()) {
            documents.put(doc.id(), doc);
            routes.computeIfAbsent(doc.id(), x -> new HashSet<>()).add("TOPIC");
        }
        for (var hit : nearest.join()) {
            documents.put(hit.document().id(), hit.document());
            similarities.put(hit.document().id(), hit.similarity());
            routes.computeIfAbsent(hit.document().id(), x -> new HashSet<>()).add("SIMILAR");
        }
        for (var post : recent(category, 1, 200, false)) {
            byId.put(post.getId(), post);
            routes.computeIfAbsent(post.getId(), x -> new HashSet<>()).add("NEW");
        }
        var missing = routes
            .keySet()
            .stream()
            .filter(id -> !byId.containsKey(id))
            .toList();
        if (!missing.isEmpty()) posts.selectByIds(missing).forEach(p -> byId.put(p.getId(), p));
        var history = cold
            ? List.<RecommendationProfileService.Interaction>of()
            : profiles.interactions(user);
        var seedIds = history
            .stream()
            .map(RecommendationProfileService.Interaction::post)
            .distinct()
            .toList();
        var allIds = new HashSet<>(routes.keySet());
        allIds.addAll(seedIds);
        try {
            documents.putAll(vectors.batch(new ArrayList<>(allIds)));
        } catch (Exception ignored) {}
        var seeds = new HashMap<Long, PostEntity>();
        if (!seedIds.isEmpty()) posts.selectByIds(seedIds).forEach(p -> seeds.put(p.getId(), p));
        var userVector = cold ? List.<Float>of() : readVector(user);
        var seen = redis.opsForZSet().range("content:rec:seen:" + user, 0, -1);
        if (seen == null) seen = Set.of();
        // 点过「不感兴趣」的帖子一律不再出现；只是看过的帖子排到未读之后，免得内容少时整页为空
        var dismissed = new HashSet<>(
            jdbc.queryForList(
                "SELECT post_id FROM recommend_feedback WHERE user_id=? AND delete_state=0 ORDER BY id DESC LIMIT 1000",
                Long.class,
                user
            )
        );
        var read = new HashSet<Long>();
        var mute = redis.opsForHash().entries("content:rec:mute-author:" + user);
        double maximum = byId
            .values()
            .stream()
            .mapToDouble(p -> Math.log1p(p.getHotScore()))
            .max()
            .orElse(1);
        double total = profile
            .entrySet()
            .stream()
            .filter(e -> !e.getKey().startsWith("_"))
            .mapToDouble(Map.Entry::getValue)
            .sum();
        var ranked = new ArrayList<Entry>();
        var scores = new HashMap<Long, Double>();
        var learned = learnedWeights();
        var weights = List.of(
            environment.getProperty("meng.content.rec.weight.hot", Double.class, .30),
            environment.getProperty("meng.content.rec.weight.vector", Double.class, .25),
            environment.getProperty("meng.content.rec.weight.interest", Double.class, .20),
            environment.getProperty("meng.content.rec.weight.follow", Double.class, .15),
            environment.getProperty("meng.content.rec.weight.freshness", Double.class, .10)
        );
        for (var id : routes.keySet()) {
            var post = byId.get(id);
            if (
                post == null ||
                post.getAuthorId() == user ||
                !"NORMAL".equals(post.getReviewState().name()) ||
                dismissed.contains(id)
            ) continue;
            if (seen.contains(String.valueOf(id))) read.add(id);
            Object muted = mute.get(String.valueOf(post.getAuthorId()));
            if (
                !followees.contains(post.getAuthorId()) &&
                muted != null &&
                Long.parseLong(muted.toString()) > clock.instant().getEpochSecond()
            ) continue;
            var doc = documents.get(id);
            int quality = doc == null ? 3 : doc.quality();
            String verdict = doc == null ? "PASS" : doc.verdict();
            if ("BLOCK".equals(verdict)) continue;
            double freshness = Math.pow(
                0.5,
                Duration.between(
                    post.getCreateTime().toInstant(ZoneOffset.UTC),
                    clock.instant()
                ).toSeconds() /
                    3600d /
                    48
            );
            double interest = profile.getOrDefault("c:" + post.getCategory().name(), 0d);
            if (doc != null) for (String topic : doc.topics())
                interest += profile.getOrDefault("t:" + topic, 0d);
            var features = new ArrayList<>(
                List.of(
                    maximum == 0 ? 0 : (Math.log1p(post.getHotScore()) / maximum) * freshness,
                    cold || doc == null || userVector.isEmpty()
                        ? 0
                        : cosine(doc.vector(), userVector),
                    cold || total == 0 ? 0 : interest / total,
                    followees.contains(post.getAuthorId()) ? 1d : 0d,
                    freshness
                )
            );
            var hits = routes.get(id);
            String route = List.of("FOLLOW", "SIMILAR", "TOPIC", "HOT", "NEW")
                .stream()
                .filter(hits::contains)
                .findFirst()
                .orElse("HOT");
            double score = 0;
            for (int i = 0; i < 5; i++) score += features.get(i) * weights.get(i);
            for (String name : List.of("HOT", "FOLLOW", "TOPIC", "SIMILAR", "NEW"))
                features.add(hits.contains(name) ? 1d : 0d);
            if (learned != null) {
                score = learned.intercept();
                for (int i = 0; i < features.size(); i++) score +=
                    features.get(i) * learned.weights().get(i);
                score += learned.weights().get(10);
                score = 1 / (1 + Math.exp(-score));
            }
            score *=
                List.of(0.6, 0.85, 1d, 1.1, 1.2).get(quality - 1) *
                ("SUSPECT".equals(verdict) ? 0.5 : 1);
            String topic = doc == null || doc.topics().isEmpty() ? null : doc.topics().get(0);
            String similarTitle = null;
            if ("SIMILAR".equals(route) && doc != null) {
                double best = -2;
                for (long seed : seedIds) {
                    var source = documents.get(seed);
                    if (source == null || !seeds.containsKey(seed)) continue;
                    double similarity = cosine(doc.vector(), source.vector());
                    if (similarity > best) {
                        best = similarity;
                        similarTitle = seeds.get(seed).getTitle();
                    }
                }
            }
            if ("SIMILAR".equals(route) && similarTitle == null) route =
                topic == null ? "HOT" : "TOPIC";
            ranked.add(
                new Entry(
                    id,
                    route,
                    new RecommendationReason(route, null, topic, similarTitle),
                    features
                )
            );
            scores.put(id, score);
        }
        ranked.sort(
            Comparator.<Entry>comparingInt(e -> read.contains(e.id()) ? 1 : 0)
                .thenComparing(
                    Comparator.<Entry>comparingDouble(e -> scores.get(e.id())).reversed()
                )
                .thenComparing(Entry::id, Comparator.reverseOrder())
        );
        return spread(ranked, byId, routes, read);
    }

    // 从高分往下贪心取，作者与分类尽量打散，每 10 位留一个给新帖
    static List<Entry> spread(
        List<Entry> ranked,
        Map<Long, PostEntity> byId,
        Map<Long, Set<String>> routes,
        Set<Long> read
    ) {
        var output = new ArrayList<Entry>();
        var remaining = new ArrayList<>(ranked);
        while (!remaining.isEmpty() && output.size() < 200) {
            Entry chosen = null;
            Entry spaced = null;
            for (var e : remaining) {
                var post = byId.get(e.id());
                boolean authorRepeat = false;
                for (int i = Math.max(0, output.size() - 4); i < output.size(); i++) if (
                    byId.get(output.get(i).id()).getAuthorId().equals(post.getAuthorId())
                ) authorRepeat = true;
                if (authorRepeat) continue;
                if (spaced == null) spaced = e;
                int n = output.size();
                if (
                    n >= 2 &&
                    byId.get(output.get(n - 1).id()).getCategory() == post.getCategory() &&
                    byId.get(output.get(n - 2).id()).getCategory() == post.getCategory()
                ) continue;
                if (
                    n % 10 == 9 &&
                    routes.get(e.id()).contains("NEW") &&
                    !read.contains(e.id())
                ) {
                    chosen = e;
                    break;
                }
                if (chosen == null) chosen = e;
                if (n % 10 != 9) break;
            }
            // 打散只是尽量：分类连续的限制放不下时先放宽它、仍保住作者间隔，再不行才取排在最前的，不能因此截断列表
            if (chosen == null) chosen = spaced != null ? spaced : remaining.get(0);
            output.add(chosen);
            remaining.remove(chosen);
        }
        return output;
    }

    private record Learned(List<Double> weights, double intercept) {}

    // 计算候选与兴趣向量的余弦相似度
    private double cosine(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) return 0;
        double dot = 0,
            an = 0,
            bn = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            an += a.get(i) * a.get(i);
            bn += b.get(i) * b.get(i);
        }
        return an == 0 || bn == 0 ? 0 : Math.max(0, dot / Math.sqrt(an * bn));
    }

    // 仅在动态开关允许时读取已生效权重
    private Learned learnedWeights() {
        if (
            !"LEARNED".equals(environment.getProperty("meng.content.rec.ranker", "MANUAL"))
        ) return null;
        var rows = jdbc.queryForList(
            "SELECT weights,intercept FROM recommend_ranker WHERE active=1 AND delete_state=0 ORDER BY version DESC LIMIT 1"
        );
        if (rows.isEmpty()) return null;
        var weights = json.readValue(
            rows.get(0).get("weights").toString(),
            new tools.jackson.core.type.TypeReference<List<Double>>() {}
        );
        return weights.size() == 11
            ? new Learned(weights, ((Number) rows.get(0).get("intercept")).doubleValue())
            : null;
    }

    // 动态配置读取
    @Autowired
    private org.springframework.core.env.Environment environment;

    // 检查索引 generation 后解码兴趣向量
    public List<Float> readVector(long user) {
        if (redis.opsForValue().get("content:vector:building") != null) return List.of();
        String active = Objects.toString(
            redis.opsForValue().get("content:vector:active"),
            "meng_vector_post_blue"
        );
        if (
            !active.equals(redis.opsForValue().get("content:rec:vec-version:" + user))
        ) return List.of();
        String value = redis.opsForValue().get("content:rec:vec:" + user);
        if (value == null) return List.of();
        var buffer = ByteBuffer.wrap(Base64.getDecoder().decode(value)).order(
            ByteOrder.LITTLE_ENDIAN
        );
        var values = new ArrayList<Float>();
        while (buffer.remaining() >= 4) values.add(buffer.getFloat());
        return values;
    }
}

package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.content.client.AiClient;
import com.pluchon.meng.content.client.AiComputation;
import com.pluchon.meng.content.controller.vo.PostFeedVO;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.mapper.PostMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 相关帖子与推荐作者召回
@Service
public class RelatedPostService {

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

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    // 限制候选总数与每位作者的展示数量。
    private static final int MAX_RELATED = 20;
    private static final int PER_AUTHOR = 3;

    @Autowired
    private AiClient ai;

    // 向量提供公开候选，由 agent 判断与源帖的相关性；AI 不可用时按向量相似度返回，不按标签补齐。
    public PageResult<PostFeedVO> related(long id, long page, long size) {
        var post = posts.selectById(id);
        if (
            post == null ||
            (!"NORMAL".equals(post.getReviewState().name()) &&
                post.getAuthorId() != UserContext.require().userId())
        ) throw new BusinessException(CommonErrorCode.NOT_FOUND);
        String key = "content:related:" + id;
        List<Long> ids = null;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                var entry = json.readTree(cached);
                if ("agent-v1".equals(entry.path("source").asString())) ids =
                    json.convertValue(
                        entry.path("ids"),
                        new tools.jackson.core.type.TypeReference<
                            List<Long>
                        >() {}
                    );
            }
        } catch (RuntimeException ignored) {}
        if (ids == null) {
            var selected = new ArrayList<Long>();
            var perAuthor = new HashMap<Long, Integer>();
            boolean degraded = false;
            try {
                var doc = vectors.get(id);
                if (doc != null) {
                    var nearest = vectors
                        .nearest(doc.vector(), null, 0, 21)
                        .stream()
                        .filter(hit -> hit.document().id() != id)
                        .limit(MAX_RELATED)
                        .map(hit -> hit.document().id())
                        .toList();
                    var visible = new HashMap<Long, PostEntity>();
                    if (!nearest.isEmpty()) posts
                        .selectByIds(nearest)
                        .stream()
                        .filter(
                            item ->
                                "NORMAL".equals(item.getReviewState().name()) &&
                                Integer.valueOf(0).equals(item.getDeleteState())
                        )
                        .forEach(item -> visible.put(item.getId(), item));
                    var candidates = nearest
                        .stream()
                        .map(visible::get)
                        .filter(Objects::nonNull)
                        .toList();
                    if (!candidates.isEmpty()) {
                        List<PostEntity> ranked;
                        try {
                            var query =
                                "选择与这篇帖子的主题、经历或问题真正相关的帖子：" +
                                post.getTitle() +
                                "\n" +
                                Objects.toString(post.getSummary(), "");
                            var result = ai.retrieve(
                                new AiComputation.RetrievalRequest(
                                    query,
                                    candidates
                                        .stream()
                                        .map(
                                            item ->
                                                item.getTitle() +
                                                "\n" +
                                                Objects.toString(
                                                    item.getSummary(),
                                                    ""
                                                )
                                        )
                                        .toList(),
                                    MAX_RELATED,
                                    UserContext.require().userId()
                                )
                            );
                            if (
                                result == null ||
                                result.ranked() == null ||
                                new HashSet<>(result.ranked()).size() !=
                                    result.ranked().size() ||
                                result
                                    .ranked()
                                    .stream()
                                    .anyMatch(
                                        index ->
                                            index == null ||
                                            index < 0 ||
                                            index >= candidates.size()
                                    )
                            ) throw new IllegalStateException(
                                "Invalid related agent selection"
                            );
                            ranked = result
                                .ranked()
                                .stream()
                                .map(candidates::get)
                                .toList();
                        } catch (Exception agentUnavailable) {
                            // AI 不可用时按向量相似度的顺序返回，不写缓存，下次请求再交给 agent
                            ranked = candidates;
                            degraded = true;
                        }
                        for (var item : ranked) {
                            if (
                                perAuthor.merge(
                                    item.getAuthorId(),
                                    1,
                                    Integer::sum
                                ) <= PER_AUTHOR
                            ) selected.add(item.getId());
                        }
                    }
                }
            } catch (Exception vectorUnavailable) {
                // 向量索引也不可用时相关帖子留空，不影响详情页其他部分
                selected.clear();
                degraded = true;
            }
            ids = selected;
            if (!degraded) try {
                redis
                    .opsForValue()
                    .set(
                        key,
                        json.writeValueAsString(
                            Map.of("source", "agent-v1", "ids", ids)
                        ),
                        Duration.ofHours(1)
                    );
            } catch (RuntimeException ignored) {}
        }
        long safeSize = Math.min(10, Math.max(1, size));
        long safePage = Math.max(1, page);
        int start = (int) Math.min(ids.size(), (safePage - 1) * safeSize);
        var slice = ids.subList(
            start,
            (int) Math.min(ids.size(), start + safeSize)
        );
        if (slice.isEmpty()) return new PageResult<>(
            List.of(),
            ids.size(),
            safePage,
            safeSize
        );
        var rows = new HashMap<Long, PostEntity>();
        posts
            .selectByIds(slice)
            .stream()
            .filter(p -> "NORMAL".equals(p.getReviewState().name()))
            .forEach(p -> rows.put(p.getId(), p));
        return new PageResult<>(
            feed.hydrate(
                slice.stream().map(rows::get).filter(Objects::nonNull).toList()
            ),
            ids.size(),
            safePage,
            safeSize
        );
    }

    // 一次最多备选几位作者（换一批最多翻到这里），备选清单缓存多久
    private static final int MAX_AUTHORS = 30;
    private static final Duration AUTHORS_TTL = Duration.ofMinutes(10);

    // 分页读取推荐关注：第 1 页重新算并缓存备选清单，后面的页读缓存，换一批时不会重复；已关注的人每次都剔掉
    public PageResult<Map<String, Object>> authors(long page, long size) {
        long user = UserContext.require().userId();
        long safeSize = Math.min(10, Math.max(1, size));
        long safePage = Math.max(1, page);
        String key = "content:rec:authors:" + user;
        List<Map<String, Object>> all = null;
        if (safePage > 1) try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) all = json.readValue(
                cached,
                new tools.jackson.core.type.TypeReference<
                    List<Map<String, Object>>
                >() {}
            );
        } catch (RuntimeException ignored) {}
        if (all == null) {
            all = json.convertValue(
                buildAuthors(user),
                new tools.jackson.core.type.TypeReference<
                    List<Map<String, Object>>
                >() {}
            );
            try {
                redis
                    .opsForValue()
                    .set(key, json.writeValueAsString(all), AUTHORS_TTL);
            } catch (RuntimeException ignored) {}
        }
        Set<Long> following;
        try {
            following = new HashSet<>(users.followees(user).data());
        } catch (RuntimeException unavailable) {
            following = Set.of();
        }
        final var followed = following;
        var remaining = all
            .stream()
            .filter(
                item ->
                    !followed.contains(
                        (
                            (Number) ((Map<?, ?>) item.get("author")).get("id")
                        ).longValue()
                    )
            )
            .toList();
        int start = (int) Math.min(remaining.size(), (safePage - 1) * safeSize);
        return new PageResult<>(
            remaining.subList(
                start,
                (int) Math.min(remaining.size(), start + safeSize)
            ),
            remaining.size(),
            safePage,
            safeSize
        );
    }

    // 按互动、相似内容和热度排出备选作者
    private List<Map<String, Object>> buildAuthors(long user) {
        int limit = MAX_AUTHORS;
        var rows = jdbc.queryForList(
            "SELECT p.author_id,COUNT(DISTINCT a.post_id) interactions FROM post p JOIN (SELECT post_id FROM post_like WHERE user_id=? AND create_time>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 DAY) UNION ALL SELECT post_id FROM post_favorite WHERE user_id=? AND create_time>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 DAY) UNION ALL SELECT post_id FROM comment WHERE author_id=? AND delete_state=0 AND create_time>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 30 DAY)) a ON a.post_id=p.id WHERE p.delete_state=0 AND p.review_state='NORMAL' AND p.author_id<>? GROUP BY p.author_id ORDER BY interactions DESC LIMIT 50",
            user,
            user,
            user,
            user
        );
        var counts = new LinkedHashMap<Long, Long>();
        for (var row : rows)
            counts.put(
                ((Number) row.get("author_id")).longValue(),
                ((Number) row.get("interactions")).longValue()
            );
        try {
            var vector = recommendations.readVector(user);
            if (!vector.isEmpty()) for (var hit : vectors.nearest(
                vector,
                null,
                30,
                50
            ))
                counts.putIfAbsent(hit.document().authorId(), 0L);
        } catch (Exception ignored) {}
        for (var row : jdbc.queryForList(
            "SELECT author_id,MAX(hot_score) score FROM post WHERE delete_state=0 AND review_state='NORMAL' AND create_time>=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 7 DAY) AND author_id<>? GROUP BY author_id ORDER BY score DESC LIMIT 50",
            user
        ))
            counts.putIfAbsent(((Number) row.get("author_id")).longValue(), 0L);
        counts.remove(user);
        var topics = new HashMap<Long, String>();
        if (!counts.isEmpty()) {
            String placeholders = String.join(
                ",",
                Collections.nCopies(counts.size(), "?")
            );
            for (var row : jdbc.queryForList(
                "SELECT p.author_id,f.topics FROM post p JOIN post_ai_feature f ON f.post_id=p.id AND f.delete_state=0 WHERE p.author_id IN (" +
                    placeholders +
                    ") AND p.delete_state=0 AND p.review_state='NORMAL' ORDER BY p.hot_score DESC,p.id DESC LIMIT 300",
                counts.keySet().toArray()
            )) {
                var values = json.readTree(row.get("topics").toString());
                if (!values.isEmpty()) topics.putIfAbsent(
                    ((Number) row.get("author_id")).longValue(),
                    values.get(0).asString()
                );
            }
        }
        var profiles = feedAuthors(counts.keySet().stream().toList());
        var output = new ArrayList<Map<String, Object>>();
        Set<Long> following;
        try {
            following = new HashSet<>(users.followees(user).data());
        } catch (RuntimeException e) {
            return List.of();
        }
        for (var entry : counts.entrySet()) {
            var profile = profiles.get(entry.getKey());
            if (profile == null || following.contains(profile.id())) continue;
            var item = new LinkedHashMap<String, Object>();
            item.put("author", profile);
            item.put("interactions", entry.getValue());
            String topic = topics.get(entry.getKey());
            item.put(
                "reason",
                entry.getValue() > 0
                    ? "INTERACTION"
                    : topic == null
                      ? "HOT"
                      : "TOPIC"
            );
            if (topic != null) item.put("topic", topic);
            output.add(item);
            if (output.size() == limit) break;
        }
        return output;
    }

    // 批量查询正常账号公开资料
    @Autowired
    private AuthorService authorService;

    // 推荐兴趣向量查询
    @Autowired
    private RecommendationService recommendations;

    // 公开用户与关注关系客户端
    @Autowired
    private com.pluchon.meng.content.client.UserClient users;

    // 批量读取公开作者资料
    private Map<Long, com.pluchon.meng.api.user.vo.PublicUserVO> feedAuthors(
        List<Long> ids
    ) {
        return ids.isEmpty() ? Map.of() : authorService.batch(ids);
    }
}

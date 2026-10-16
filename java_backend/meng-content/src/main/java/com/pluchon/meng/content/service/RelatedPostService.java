package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.error.BusinessException;
import com.pluchon.meng.common.core.error.CommonErrorCode;
import com.pluchon.meng.common.core.page.PageResult;
import com.pluchon.meng.common.web.context.UserContext;
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
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    // 相关帖子总数上限、每位作者最多几篇、相似度下限的默认值
    private static final int MAX_RELATED = 50;
    private static final int PER_AUTHOR = 3;
    private static final double MIN_SIMILARITY = 0.42;

    // 动态配置读取
    @Autowired
    private org.springframework.core.env.Environment environment;

    // 优先向量召回，不足 5 篇时按标签与分类补齐；总数最多 50 篇，由后端分页
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
            if (cached != null) ids = json.readValue(
                cached,
                new tools.jackson.core.type.TypeReference<List<Long>>() {}
            );
        } catch (RuntimeException ignored) {}
        if (ids == null) {
            var selected = new ArrayList<Long>();
            var perAuthor = new HashMap<Long, Integer>();
            double minimum = environment.getProperty(
                "meng.content.related.min-similarity",
                Double.class,
                MIN_SIMILARITY
            );
            try {
                var doc = vectors.get(id);
                if (doc != null) for (var hit : vectors.nearest(doc.vector(), null, 180, 120))
                    if (
                        hit.document().id() != id &&
                        hit.similarity() >= minimum &&
                        perAuthor.merge(hit.document().authorId(), 1, Integer::sum) <= PER_AUTHOR
                    ) {
                        selected.add(hit.document().id());
                        if (selected.size() == MAX_RELATED) break;
                    }
            } catch (Exception ignored) {}
            if (selected.size() < 5) {
                var fallback = jdbc.queryForList(
                    "SELECT p.id,p.author_id FROM post p LEFT JOIN post_tag t ON t.post_id=p.id AND t.tag_id IN (SELECT tag_id FROM post_tag WHERE post_id=?) WHERE p.id<>? AND p.delete_state=0 AND p.review_state='NORMAL' AND (p.category=? OR t.tag_id IS NOT NULL) GROUP BY p.id,p.author_id,p.hot_score ORDER BY COUNT(t.tag_id) DESC,p.hot_score DESC,p.id DESC LIMIT 50",
                    id,
                    id,
                    post.getCategory().name()
                );
                for (var row : fallback) {
                    long candidate = ((Number) row.get("id")).longValue();
                    long author = ((Number) row.get("author_id")).longValue();
                    if (
                        !selected.contains(candidate) &&
                        perAuthor.merge(author, 1, Integer::sum) <= PER_AUTHOR
                    ) {
                        selected.add(candidate);
                        if (selected.size() == 5) break;
                    }
                }
            }
            ids = selected;
            try {
                redis.opsForValue().set(key, json.writeValueAsString(ids), Duration.ofHours(1));
            } catch (RuntimeException ignored) {}
        }
        long safeSize = Math.min(10, Math.max(1, size));
        long safePage = Math.max(1, page);
        int start = (int) Math.min(ids.size(), (safePage - 1) * safeSize);
        var slice = ids.subList(start, (int) Math.min(ids.size(), start + safeSize));
        if (slice.isEmpty()) return new PageResult<>(List.of(), ids.size(), safePage, safeSize);
        var rows = new HashMap<Long, PostEntity>();
        posts
            .selectByIds(slice)
            .stream()
            .filter(p -> "NORMAL".equals(p.getReviewState().name()))
            .forEach(p -> rows.put(p.getId(), p));
        return new PageResult<>(
            feed.hydrate(slice.stream().map(rows::get).filter(Objects::nonNull).toList()),
            ids.size(),
            safePage,
            safeSize
        );
    }

    // 按互动、相似内容和热度推荐未关注作者
    public List<Map<String, Object>> authors(int size) {
        long user = UserContext.require().userId();
        int limit = Math.min(10, Math.max(1, size));
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
            if (!vector.isEmpty()) for (var hit : vectors.nearest(vector, null, 30, 50))
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
            String placeholders = String.join(",", Collections.nCopies(counts.size(), "?"));
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
                entry.getValue() > 0 ? "INTERACTION" : topic == null ? "HOT" : "TOPIC"
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
    private Map<Long, com.pluchon.meng.api.user.vo.PublicUserVO> feedAuthors(List<Long> ids) {
        return ids.isEmpty() ? Map.of() : authorService.batch(ids);
    }
}

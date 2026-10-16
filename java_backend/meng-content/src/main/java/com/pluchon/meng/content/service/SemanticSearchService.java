package com.pluchon.meng.content.service;

import com.pluchon.meng.content.client.AiClient;
import com.pluchon.meng.content.controller.vo.HighlightSegmentVO;
import com.pluchon.meng.content.controller.vo.SearchPostVO;
import com.pluchon.meng.content.converter.SearchHighlight;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.mapper.PostMapper;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 关键词和向量召回的相关度融合
@Service
public class SemanticSearchService {

    // 纯计算 AI 客户端
    @Autowired
    private AiClient ai;

    // 帖子向量读写与召回
    @Autowired
    private PostVectorIndex vectors;

    // 帖子数据与内容版本
    @Autowired
    private PostMapper posts;

    // 帖子展示数据批量组装
    @Autowired
    private PostService feed;

    // 关键词搜索降级路径
    @Autowired
    private PostSearchService keywords;

    // 搜索频率准入
    @Autowired
    private ContentLimitService limits;

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 动态配置读取
    @Autowired
    private org.springframework.core.env.Environment environment;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public record Result(
        List<SearchPostVO> records,
        long total,
        long page,
        long size,
        boolean semantic
    ) {}

    // 先限流，再做向量与关键词融合，失败退回关键词
    public Result search(String input, PostCategory category, long page, long size) {
        String q = java.text.Normalizer.normalize(
            input.strip(),
            java.text.Normalizer.Form.NFKC
        ).toLowerCase(Locale.ROOT);
        if (
            q.isBlank() ||
            q.codePointCount(0, q.length()) > 50 ||
            page < 1 ||
            size < 1 ||
            size > 50 ||
            page > 10000 / size
        ) throw new com.pluchon.meng.common.core.error.BusinessException(
            com.pluchon.meng.common.core.error.CommonErrorCode.INVALID_ARGUMENT
        );
        limits.search(com.pluchon.meng.common.web.context.UserContext.require().userId());
        PostVectorIndex.Hybrid matches;
        try {
            if (
                redis.opsForValue().get("content:vector:building") != null
            ) throw new IllegalStateException("Vector rebuild in progress");
            String active = Objects.toString(
                redis.opsForValue().get("content:vector:active"),
                "meng_vector_post_blue"
            );
            String key =
                "content:qvec:" +
                active +
                ":" +
                org.springframework.util.DigestUtils.md5DigestAsHex(
                    q.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
            String cached = redis.opsForValue().get(key);
            List<Float> vector;
            if (cached != null) vector = json.readValue(
                cached,
                new tools.jackson.core.type.TypeReference<List<Float>>() {}
            );
            else {
                var response = ai.embeddings(Map.of("texts", List.of(q)));
                var batches = json.convertValue(
                    response.get("vectors"),
                    new tools.jackson.core.type.TypeReference<List<List<Float>>>() {}
                );
                vector = batches.get(0);
                redis.opsForValue().set(key, json.writeValueAsString(vector), Duration.ofDays(1));
            }
            // 只要真正相近的：太远的向量命中不算，否则任何搜索词都会带出一串不相干的帖子
            matches = vectors.hybrid(
                q,
                vector,
                category,
                environment.getProperty("meng.content.search.min-similarity", Float.class, 0.34f)
            );
        } catch (Exception unavailable) {
            var result = keywords.search(input, category, PostSort.RELEVANT, page, size, false);
            return new Result(result.records(), result.total(), page, size, false);
        }
        var scores = new HashMap<Long, Double>();
        for (var list : List.of(matches.keyword(), matches.semantic()))
            for (int i = 0; i < list.size(); i++) scores.merge(
                list.get(i),
                1d / (61 + i),
                Double::sum
            );
        var ids = scores
            .keySet()
            .stream()
            .sorted(
                Comparator.<Long>comparingDouble(scores::get)
                    .reversed()
                    .thenComparing(Comparator.reverseOrder())
            )
            .toList();
        var rows = new HashMap<Long, PostEntity>();
        if (!ids.isEmpty()) posts
            .selectByIds(ids)
            .stream()
            .filter(p -> "NORMAL".equals(p.getReviewState().name()))
            .forEach(p -> rows.put(p.getId(), p));
        var ordered = ids.stream().map(rows::get).filter(Objects::nonNull).toList();
        int start = (int) Math.min(ordered.size(), (page - 1) * size),
            end = (int) Math.min(ordered.size(), start + size);
        var keywordIds = new HashSet<>(matches.keyword());
        var records = feed
            .hydrate(ordered.subList(start, end))
            .stream()
            .map(p ->
                new SearchPostVO(
                    p.id(),
                    p.category(),
                    p.title(),
                    p.summary(),
                    p.images(),
                    p.imageCount(),
                    p.viewCount(),
                    p.likeCount(),
                    p.commentCount(),
                    p.favoriteCount(),
                    p.liked(),
                    p.favorited(),
                    p.ipRegion(),
                    p.createTime(),
                    p.author(),
                    keywordIds.contains(p.id())
                        ? SearchHighlight.literal(p.title(), input)
                        : List.of(new HighlightSegmentVO(p.title(), false)),
                    List.of(new HighlightSegmentVO(p.summary(), false))
                )
            )
            .toList();
        return new Result(records, ordered.size(), page, size, true);
    }
}

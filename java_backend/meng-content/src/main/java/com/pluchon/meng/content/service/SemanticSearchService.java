package com.pluchon.meng.content.service;

import com.pluchon.meng.content.client.AiClient;
import com.pluchon.meng.content.client.AiComputation;
import com.pluchon.meng.content.controller.vo.HighlightSegmentVO;
import com.pluchon.meng.content.controller.vo.SearchPostVO;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.mapper.PostMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 向量只生成公开候选，结果由检索 agent 按含义判断；AI 不可用时退回关键词检索，结果照常返回。
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

    // AI 不可用时的关键词检索
    @Autowired
    private PostSearchService keyword;

    // 搜索频率准入
    @Autowired
    private ContentLimitService limits;

    // 派生缓存、画像与配额
    @Autowired
    private StringRedisTemplate redis;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();

    public record Result(
        List<SearchPostVO> records,
        long total,
        long page,
        long size,
        boolean semantic
    ) {}

    // 身份、公开性与限流由业务端校验，相关性与排序交给 agent。
    public Result search(
        String input,
        PostCategory category,
        long page,
        long size
    ) {
        String q = java.text.Normalizer.normalize(
            input.strip(),
            java.text.Normalizer.Form.NFKC
        );
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
        limits.search(
            com.pluchon.meng.common.web.context.UserContext.require().userId()
        );
        List<PostEntity> ordered;
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
                    new tools.jackson.core.type.TypeReference<
                        List<List<Float>>
                    >() {}
                );
                vector = batches.get(0);
                redis
                    .opsForValue()
                    .set(
                        key,
                        json.writeValueAsString(vector),
                        Duration.ofDays(1)
                    );
            }
            var ids = vectors
                .nearest(vector, category, 0, 20)
                .stream()
                .map(hit -> hit.document().id())
                .toList();
            var rows = new HashMap<Long, PostEntity>();
            if (!ids.isEmpty()) posts
                .selectByIds(ids)
                .stream()
                .filter(p -> "NORMAL".equals(p.getReviewState().name()))
                .filter(p -> Integer.valueOf(0).equals(p.getDeleteState()))
                .forEach(p -> rows.put(p.getId(), p));
            var candidates = ids
                .stream()
                .map(rows::get)
                .filter(Objects::nonNull)
                .toList();
            if (candidates.isEmpty()) return new Result(
                List.of(),
                0,
                page,
                size,
                true
            );
            var decision = ai.retrieve(
                new AiComputation.RetrievalRequest(
                    input,
                    candidates
                        .stream()
                        .map(post ->
                            json.writeValueAsString(
                                Map.of(
                                    "id",
                                    post.getId(),
                                    "title",
                                    post.getTitle(),
                                    "content",
                                    Objects.toString(post.getSummary(), ""),
                                    "createdAt",
                                    post.getCreateTime().toString()
                                )
                            )
                        )
                        .toList(),
                    20,
                    com.pluchon.meng.common.web.context.UserContext.require().userId()
                )
            );
            if (
                decision == null ||
                decision.ranked() == null ||
                new HashSet<>(decision.ranked()).size() !=
                    decision.ranked().size() ||
                decision
                    .ranked()
                    .stream()
                    .anyMatch(
                        index ->
                            index == null ||
                            index < 0 ||
                            index >= candidates.size()
                    )
            ) throw new IllegalStateException(
                "Invalid agent retrieval selection"
            );
            ordered = decision.ranked().stream().map(candidates::get).toList();
        } catch (Exception unavailable) {
            // 准入已在上面扣过，关键词检索不再重复计次；响应带 semantic=false，前端不提示错误。
            var fallback = keyword.search(
                input,
                category,
                com.pluchon.meng.content.entity.enums.PostSort.RELEVANT,
                page,
                size,
                false
            );
            return new Result(
                fallback.records(),
                fallback.total(),
                fallback.page(),
                fallback.size(),
                false
            );
        }
        int start = (int) Math.min(ordered.size(), (page - 1) * size),
            end = (int) Math.min(ordered.size(), start + size);
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
                    List.of(new HighlightSegmentVO(p.title(), false)),
                    List.of(new HighlightSegmentVO(p.summary(), false))
                )
            )
            .toList();
        return new Result(records, ordered.size(), page, size, true);
    }
}

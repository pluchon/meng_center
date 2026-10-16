package com.pluchon.meng.content.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.enums.PostCategory;
import java.io.StringReader;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

// 帖子向量索引读写与蓝绿切换
@Service
public class PostVectorIndex {

    private static final String ALIAS = "meng_vector_post";

    // 内容域专属 Elasticsearch 客户端
    @Autowired
    private ElasticsearchClient client;

    // 派生缓存、画像与配额
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redis;

    private static final String BUILDING = "content:vector:building";
    private static final String PENDING = "content:vector:pending";
    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    public record Document(
        long id,
        List<Float> vector,
        String category,
        List<String> topics,
        long authorId,
        int quality,
        String verdict,
        String createTime,
        String modelVersion
    ) {}

    public record Hit(Document document, double similarity) {}

    // 按需建立首个索引与读别名
    public synchronized void ensure() throws java.io.IOException {
        if (
            client
                .indices()
                .existsAlias(a -> a.name(ALIAS))
                .value()
        ) return;
        String name = ALIAS + "_blue";
        if (
            !client
                .indices()
                .exists(i -> i.index(name))
                .value()
        ) create(name);
        client.indices().putAlias(a -> a.index(name).name(ALIAS));
    }

    // 创建固定维度和显式量化配置的索引
    private void create(String name) throws java.io.IOException {
        client.indices().create(i ->
            i.index(name).withJson(
                new StringReader(
                    """
                    {"settings":{"number_of_shards":1,"number_of_replicas":0},"mappings":{"dynamic":"strict","properties":{
                      "id":{"type":"long"},"vector":{"type":"dense_vector","dims":1024,"similarity":"cosine","index_options":{"type":"int8_hnsw"}},
                      "category":{"type":"keyword"},"topics":{"type":"keyword"},"authorId":{"type":"long"},
                      "quality":{"type":"byte"},"verdict":{"type":"keyword"},"createTime":{"type":"date"},"modelVersion":{"type":"keyword"}
                    }}}
                    """
                )
            )
        );
    }

    // 写入当前目标色并隔离不公开内容
    public synchronized void write(
        PostEntity post,
        List<Float> vector,
        List<String> topics,
        int quality,
        String verdict,
        String version
    ) throws java.io.IOException {
        ensure();
        if (!"NORMAL".equals(post.getReviewState().name())) {
            delete(post.getId());
            return;
        }
        var doc = new Document(
            post.getId(),
            vector,
            post.getCategory().name(),
            topics,
            post.getAuthorId(),
            quality,
            verdict,
            post.getCreateTime().atOffset(ZoneOffset.UTC).toString(),
            version
        );
        String building = redis.opsForValue().get(BUILDING),
            target = building == null ? ALIAS : building;
        if (building != null) {
            String modelKey = BUILDING + ":model";
            redis.opsForValue().setIfAbsent(modelKey, version);
            if (!version.equals(redis.opsForValue().get(modelKey))) throw new java.io.IOException(
                "Mixed embedding versions in rebuild"
            );
        }
        client.index(i -> i.index(target).id(String.valueOf(post.getId())).document(doc));
    }

    // 从生效色和重建色删除帖子
    public void delete(long id) throws java.io.IOException {
        if (
            client
                .indices()
                .existsAlias(a -> a.name(ALIAS))
                .value()
        ) client.delete(d -> d.index(ALIAS).id(String.valueOf(id)));
        String building = redis.opsForValue().get(BUILDING);
        if (building != null) client.delete(d -> d.index(building).id(String.valueOf(id)));
    }

    // 锁定并初始化未生效的一色
    public synchronized boolean beginRebuild() throws java.io.IOException {
        ensure();
        if (redis.opsForValue().get(BUILDING) != null) return false;
        var aliases = client
            .indices()
            .getAlias(a -> a.name(ALIAS))
            .aliases()
            .keySet();
        String next = aliases.contains(ALIAS + "_blue") ? ALIAS + "_green" : ALIAS + "_blue";
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(BUILDING, next))) return false;
        try {
            if (
                client
                    .indices()
                    .exists(i -> i.index(next))
                    .value()
            ) client.indices().delete(i -> i.index(next));
            create(next);
            redis.delete(PENDING);
            redis.delete(BUILDING + ":model");
            redis.opsForSet().add(PENDING, "issuing");
            return true;
        } catch (Exception error) {
            redis.delete(BUILDING);
            throw error;
        }
    }

    // 登记本批次必须完成的帖子
    public void expect(List<Long> ids) {
        if (!ids.isEmpty()) redis.opsForSet().add(
            PENDING,
            ids
                .stream()
                .map(String::valueOf)
                .toArray(String[]::new)
        );
    }

    // 判断是否需要恢复中断的分批发送
    public boolean issuing() {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(PENDING, "issuing"));
    }

    // 标记批次发完并尝试切换
    public void finishIssuing() throws java.io.IOException {
        redis.opsForSet().remove(PENDING, "issuing");
        completed(-1);
    }

    // 全部结果完成后刷新并原子切换别名
    public synchronized void completed(long id) throws java.io.IOException {
        String target = redis.opsForValue().get(BUILDING);
        if (target == null) return;
        redis.opsForSet().remove(PENDING, String.valueOf(id));
        if (!Long.valueOf(0).equals(redis.opsForSet().size(PENDING))) return;
        client.indices().refresh(r -> r.index(target));
        client
            .indices()
            .updateAliases(a ->
                a
                    .actions(v -> v.remove(r -> r.index(ALIAS + "_*").alias(ALIAS)))
                    .actions(v -> v.add(add -> add.index(target).alias(ALIAS)))
            );
        redis
            .opsForValue()
            .set("content:vector:active", target + ":" + java.util.UUID.randomUUID());
        redis.delete(java.util.List.of(BUILDING, PENDING, BUILDING + ":model"));
    }

    // 仅复制映射，不重新调用模型
    public synchronized void reindex() throws java.io.IOException {
        if (!beginRebuild()) return;
        String target = redis.opsForValue().get(BUILDING);
        var response = client.reindex(r ->
            r.source(s -> s.index(ALIAS)).dest(d -> d.index(target))
        );
        if (!response.failures().isEmpty()) throw new java.io.IOException(
            "Vector reindex rejected"
        );
        finishIssuing();
    }

    // 按时间、分类和审核结论做近邻召回
    public List<Hit> nearest(List<Float> vector, PostCategory category, int days, int size)
        throws java.io.IOException {
        var filters = new java.util.ArrayList<Map<String, Object>>();
        filters.add(Map.of("range", Map.of("createTime", Map.of("gte", "now-" + days + "d"))));
        filters.add(
            Map.of("bool", Map.of("must_not", List.of(Map.of("term", Map.of("verdict", "BLOCK")))))
        );
        if (category != null) filters.add(Map.of("term", Map.of("category", category.name())));
        var request = Map.of(
            "size",
            size,
            "knn",
            Map.of(
                "field",
                "vector",
                "query_vector",
                vector,
                "k",
                size,
                "num_candidates",
                Math.max(size, 1000),
                "filter",
                Map.of("bool", Map.of("filter", filters))
            )
        );
        var response = client.search(
            new SearchRequest.Builder()
                .index(ALIAS)
                .withJson(new StringReader(json.writeValueAsString(request)))
                .build(),
            Document.class
        );
        return response
            .hits()
            .hits()
            .stream()
            .filter(h -> h.source() != null)
            .map(h -> new Hit(h.source(), h.score() == null ? 0 : Math.max(0, h.score() * 2 - 1)))
            .toList();
    }

    // 读取单篇帖子的特征与向量
    public Document get(long id) throws java.io.IOException {
        var result = client.get(
            g -> g.index(ALIAS).id(String.valueOf(id)).sourceExcludeVectors(false),
            Document.class
        );
        return result.found() ? result.source() : null;
    }

    // 批量读取候选向量，避免逐篇请求
    public Map<Long, Document> batch(List<Long> ids) throws java.io.IOException {
        if (ids.isEmpty()) return Map.of();
        var response = client
            .withTransportOptions(options ->
                options.setParameter("_source_exclude_vectors", "false")
            )
            .mget(
                g -> g.index(ALIAS).ids(ids.stream().map(String::valueOf).toList()),
                Document.class
            );
        var result = new java.util.HashMap<Long, Document>();
        for (var item : response.docs())
            if (
                item.isResult() && item.result().found() && item.result().source() != null
            ) result.put(item.result().source().id(), item.result().source());
        return result;
    }

    public record Hybrid(List<Long> keyword, List<Long> semantic) {}

    // 一次请求并行获取关键词与向量候选
    public Hybrid hybrid(String text, List<Float> vector, PostCategory category, float similarity)
        throws java.io.IOException {
        var response = client.msearch(
            m ->
                m
                    .searches(s ->
                        s
                            .header(h -> h.index("meng_post"))
                            .body(b ->
                                b
                                    .size(100)
                                    .source(c -> c.fetch(false))
                                    .query(q ->
                                        q.bool(bool -> {
                                            bool.must(v ->
                                                v.multiMatch(mm ->
                                                    mm
                                                        .query(text)
                                                        .fields("title^3", "content", "tags^2")
                                                )
                                            );
                                            if (category != null) bool.filter(f ->
                                                f.term(t ->
                                                    t.field("category").value(category.name())
                                                )
                                            );
                                            return bool;
                                        })
                                    )
                            )
                    )
                    .searches(s ->
                        s
                            .header(h -> h.index(ALIAS))
                            .body(b ->
                                b
                                    .size(100)
                                    .source(c -> c.fetch(false))
                                    .knn(k ->
                                        k
                                            .field("vector")
                                            .queryVector(vector)
                                            .k(100)
                                            .numCandidates(1000)
                                            .similarity(similarity)
                                            .filter(f ->
                                                f.bool(bool -> {
                                                    bool.mustNot(n ->
                                                        n.term(t ->
                                                            t.field("verdict").value("BLOCK")
                                                        )
                                                    );
                                                    if (category != null) bool.filter(v ->
                                                        v.term(t ->
                                                            t
                                                                .field("category")
                                                                .value(category.name())
                                                        )
                                                    );
                                                    return bool;
                                                })
                                            )
                                    )
                            )
                    ),
            Document.class
        );
        var keyword = new java.util.ArrayList<Long>();
        var semantic = new java.util.ArrayList<Long>();
        var items = response.responses();
        if (items.get(0).isResult()) items
            .get(0)
            .result()
            .hits()
            .hits()
            .forEach(h -> keyword.add(Long.valueOf(h.id())));
        if (items.get(1).isResult()) items
            .get(1)
            .result()
            .hits()
            .hits()
            .forEach(h -> semantic.add(Long.valueOf(h.id())));
        else throw new java.io.IOException("Semantic index unavailable");
        return new Hybrid(keyword, semantic);
    }

    // 按最重的主题和分类权重召回
    public List<Document> byTopics(Map<String, Double> profile, PostCategory category)
        throws java.io.IOException {
        var should = new java.util.ArrayList<Map<String, Object>>();
        profile
            .entrySet()
            .stream()
            .filter(e -> e.getKey().startsWith("t:"))
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(10)
            .forEach(e ->
                should.add(
                    Map.of(
                        "term",
                        Map.of(
                            "topics",
                            Map.of("value", e.getKey().substring(2), "boost", e.getValue())
                        )
                    )
                )
            );
        profile
            .entrySet()
            .stream()
            .filter(e -> e.getKey().startsWith("c:"))
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(3)
            .forEach(e ->
                should.add(
                    Map.of(
                        "term",
                        Map.of(
                            "category",
                            Map.of("value", e.getKey().substring(2), "boost", e.getValue())
                        )
                    )
                )
            );
        if (should.isEmpty()) return List.of();
        var filters = new java.util.ArrayList<Map<String, Object>>();
        filters.add(Map.of("range", Map.of("createTime", Map.of("gte", "now-14d"))));
        if (category != null) filters.add(Map.of("term", Map.of("category", category.name())));
        var query = Map.of(
            "size",
            200,
            "query",
            Map.of(
                "bool",
                Map.of(
                    "should",
                    should,
                    "minimum_should_match",
                    1,
                    "filter",
                    filters,
                    "must_not",
                    List.of(Map.of("term", Map.of("verdict", "BLOCK")))
                )
            )
        );
        return client
            .search(
                new SearchRequest.Builder()
                    .index(ALIAS)
                    .withJson(new StringReader(json.writeValueAsString(query)))
                    .build(),
                Document.class
            )
            .hits()
            .hits()
            .stream()
            .map(h -> h.source())
            .filter(java.util.Objects::nonNull)
            .toList();
    }
}

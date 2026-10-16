package com.pluchon.meng.content.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.util.NamedValue;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.content.config.SearchProperties;
import com.pluchon.meng.content.controller.vo.HighlightSegmentVO;
import com.pluchon.meng.content.converter.SearchHighlight;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.SearchDocument;
import com.pluchon.meng.content.entity.enums.PostCategory;
import com.pluchon.meng.content.entity.enums.PostSort;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.PostMapper;
import com.pluchon.meng.content.mapper.TagMapper;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 帖子搜索索引维护、检索与蓝绿重建
@Service
public class PostSearchIndex {

    // 运行日志
    private static final Logger LOG = LoggerFactory.getLogger(PostSearchIndex.class);

    // 搜索索引别名
    private static final String ALIAS = "meng_post";

    // 缓存键前缀
    private static final String ROOT = "meng:content:search:";

    // 仅允许锁持有者释放锁
    private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
        "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1])" +
            " end return 0",
        Long.class
    );

    // 帖子搜索客户端
    @Autowired
    private ElasticsearchClient client;

    // 缓存和操作计数
    @Autowired
    private StringRedisTemplate redis;

    // 帖子数据存取
    @Autowired
    private PostMapper posts;

    // 系统标签数据存取
    @Autowired
    private TagMapper tags;

    // 正文摘要
    @Autowired
    private PostSummary summary;

    // 当前业务域配置
    @Autowired
    private SearchProperties properties;

    // 搜索文档增量写入线程池
    @Autowired
    @Qualifier("contentIndexExecutor")
    private ThreadPoolTaskExecutor writes;

    // 索引重建维护线程池
    @Autowired
    @Qualifier("contentMaintenanceExecutor")
    private ThreadPoolTaskExecutor maintenance;

    // 协调索引增量写入与批次切换
    private final Object indexWrites = new Object();

    // 搜索失败后的下次尝试时间
    private final AtomicLong retryAfter = new AtomicLong();

    // 是否已有索引重建任务排队
    private final AtomicBoolean rebuilding = new AtomicBoolean();

    // 单条搜索命中及高亮片段
    public record Hit(
        // 记录主键
        long id,
        // 标题
        List<HighlightSegmentVO> title,
        // 正文
        List<HighlightSegmentVO> content
    ) {}

    // 搜索命中集合与总数
    public record Matches(
        // 搜索命中集合
        List<Hit> hits,
        // 命中记录总数
        long total
    ) {}

    // 检索主键和安全高亮片段，失败时进入冷却
    public Matches search(String q, PostCategory category, PostSort sort, long page, long size) {
        if (System.nanoTime() < retryAfter.get()) return null;
        try {
            String token = UUID.randomUUID().toString();
            String before = "\uE000" + token + ":";
            String after = "\uE001" + token + ":";
            var request = new SearchRequest.Builder()
                .index(ALIAS)
                .size(sort == PostSort.HOT ? 1000 : (int) size)
                .from(sort == PostSort.HOT ? 0 : (int) ((page - 1) * size))
                .trackTotalHits(t -> t.enabled(true))
                .source(v -> v.fetch(false))
                .query(query(q, category))
                .highlight(highlight(before, after));
            if (sort == PostSort.LATEST) {
                request.sort(v -> v.field(f -> f.field("createTime").order(SortOrder.Desc)));
                request.sort(v -> v.field(f -> f.field("id").order(SortOrder.Desc)));
            }
            var response = client.search(request.build(), Void.class);
            var hits = new ArrayList<Hit>();
            for (var hit : response.hits().hits()) {
                List<String> title = hit.highlight().get("title");
                List<String> content = hit.highlight().get("content");
                hits.add(
                    new Hit(
                        Long.parseLong(hit.id()),
                        title == null
                            ? List.of()
                            : SearchHighlight.marked(title.get(0), before, after),
                        content == null
                            ? List.of()
                            : SearchHighlight.marked(content.get(0), before, after)
                    )
                );
            }
            return new Matches(
                List.copyOf(hits),
                sort == PostSort.HOT
                    ? hits.size()
                    : response.hits().total() == null
                      ? hits.size()
                      : response.hits().total().value()
            );
        } catch (Exception e) {
            retryAfter.set(System.nanoTime() + properties.failureCooldown().toNanos());
            LOG.warn("Search index unavailable; using title fallback");
            return null;
        }
    }

    // 事务提交后排队同步受影响的帖子
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void changed(PostReadModelEvent event) {
        if (event.kind() == PostReadModelEvent.Kind.SCORE) return;
        try {
            writes.execute(() -> synchronize(event.id()));
        } catch (RuntimeException e) {
            LOG.warn("Index queue full for post {}", event.id());
        }
    }

    // 合并业务变化并安排索引更新
    private void synchronize(long id) {
        synchronized (indexWrites) {
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    var post = posts.selectById(id);
                    var building = redis.opsForValue().get(ROOT + "building");
                    write(ALIAS, post, id);
                    if (building != null) write(building, post, id);
                    return;
                } catch (Exception e) {
                    if (attempt == 2) LOG.warn("Index synchronization failed for post {}", id);
                }
            }
        }
    }

    // 读取当前帖子状态并重试文档写入
    private void write(String index, PostEntity post, long id) throws Exception {
        if (post == null || !"NORMAL".equals(post.getReviewState().name())) client.delete(d ->
            d.index(index).id(Long.toString(id))
        );
        else client.index(i ->
            i
                .index(index)
                .id(Long.toString(id))
                .document(document(post, tags.selectNamesByPostId(id)))
        );
    }

    // 从帖子与标签生成搜索文档
    private SearchDocument document(PostEntity post, List<String> names) {
        return new SearchDocument(
            post.getId(),
            post.getTitle(),
            summary.plain(post.getContent()),
            names,
            post.getCategory().name(),
            post.getCreateTime().toInstant(ZoneOffset.UTC).toString()
        );
    }

    // 服务启动后异步检查搜索别名
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        requestRebuild(true);
    }

    // 合并维护请求并异步重建缺失索引
    public void requestRebuild(boolean onlyMissing) {
        if (!rebuilding.compareAndSet(false, true)) return;
        try {
            maintenance.execute(() -> {
                try {
                    if (
                        !onlyMissing ||
                        !client
                            .indices()
                            .existsAlias(a -> a.name(ALIAS))
                            .value()
                    ) rebuild();
                } catch (Exception e) {
                    LOG.warn("Index initialization unavailable");
                } finally {
                    rebuilding.set(false);
                }
            });
        } catch (RuntimeException e) {
            rebuilding.set(false);
            LOG.warn("Index maintenance queue full");
        }
    }

    // 组装关键词匹配、权重和分类过滤条件
    private Query query(String word, PostCategory category) {
        var builder = new BoolQuery.Builder();
        builder.must(
            Query.of(q ->
                q.multiMatch(m ->
                    m
                        .query(word)
                        .fields("title^3", "tags^2", "content")
                        .minimumShouldMatch("2<-25%")
                )
            )
        );
        builder.should(Query.of(q -> q.matchPhrase(p -> p.field("title").query(word).boost(3f))));
        builder.should(
            Query.of(q ->
                q.term(t -> t.field("tags.raw").value(word.toLowerCase(Locale.ROOT)).boost(2f))
            )
        );
        if (category != null) builder.filter(
            Query.of(q -> q.term(t -> t.field("category").value(category.name())))
        );
        return Query.of(q -> q.bool(builder.build()));
    }

    // 配置标题和正文的高亮片段
    private Highlight highlight(String before, String after) {
        var title = new NamedValue<>("title", HighlightField.of(f -> f.numberOfFragments(0)));
        var content = new NamedValue<>(
            "content",
            HighlightField.of(f -> f.fragmentSize(120).numberOfFragments(1))
        );
        return Highlight.of(h -> h.preTags(before).postTags(after).fields(title, content));
    }

    // 配置正文与标题的分词字段
    private Property textProperty() {
        return Property.of(p ->
            p.text(t ->
                t.analyzer(properties.analyzer()).searchAnalyzer(properties.searchAnalyzer())
            )
        );
    }

    // 创建待构建索引及字段映射
    private void create(String index) throws Exception {
        if (
            client
                .indices()
                .exists(e -> e.index(index))
                .value()
        ) client.indices().delete(d -> d.index(index));
        var tagProperty = Property.of(p ->
            p.text(t ->
                t
                    .analyzer(properties.analyzer())
                    .searchAnalyzer(properties.searchAnalyzer())
                    .fields("raw", f -> f.keyword(k -> k.normalizer("lowercase")))
            )
        );
        var mapping = TypeMapping.of(m ->
            m
                .dynamic(DynamicMapping.Strict)
                .properties(
                    Map.of(
                        "id",
                        Property.of(p -> p.long_(l -> l)),
                        "title",
                        textProperty(),
                        "content",
                        textProperty(),
                        "tags",
                        tagProperty,
                        "category",
                        Property.of(p -> p.keyword(k -> k)),
                        "createTime",
                        Property.of(p -> p.date(d -> d))
                    )
                )
        );
        client.indices().create(c ->
            c
                .index(index)
                .mappings(mapping)
                .settings(s ->
                    s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                        .analysis(a ->
                            a.normalizer("lowercase", n -> n.custom(v -> v.filter("lowercase")))
                        )
                )
        );
    }

    // 组装批量写入的文档操作
    private BulkOperation operation(String index, PostEntity post, List<String> names) {
        var document = document(post, names);
        return BulkOperation.of(b ->
            b.index(i -> i.index(index).id(Long.toString(post.getId())).document(document))
        );
    }

    // 分批构建、校验数量并原子切换索引别名
    public boolean rebuild() {
        String token = UUID.randomUUID().toString();
        String lock = ROOT + "lock";
        boolean locked = false;
        try {
            locked = Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(lock, token, Duration.ofMinutes(30))
            );
            if (!locked) return true;
            var aliases = client
                .indices()
                .existsAlias(a -> a.name(ALIAS))
                .value()
                ? client
                      .indices()
                      .getAlias(a -> a.name(ALIAS))
                      .aliases()
                      .keySet()
                : Set.<String>of();
            String next = aliases.contains("meng_post_blue") ? "meng_post_green" : "meng_post_blue";
            create(next);
            redis.opsForValue().set(ROOT + "building", next, Duration.ofMinutes(30));
            long cursor = 0;
            while (true) {
                synchronized (indexWrites) {
                    var rows = posts.selectRebuildBatch(cursor, null);
                    if (rows.isEmpty()) break;
                    var ids = rows.stream().map(PostEntity::getId).toList();
                    var names = new HashMap<Long, List<String>>();
                    for (var tag : tags.selectNamesByPostIds(ids))
                        names.computeIfAbsent(tag.postId(), k -> new ArrayList<>()).add(tag.name());
                    var operations = new ArrayList<BulkOperation>();
                    for (var post : rows)
                        if ("NORMAL".equals(post.getReviewState().name())) operations.add(
                            operation(next, post, names.getOrDefault(post.getId(), List.of()))
                        );
                    var result = client.bulk(b -> b.operations(operations));
                    if (result.errors()) throw new IllegalStateException("Index batch rejected");
                    cursor = rows.get(rows.size() - 1).getId();
                }
            }
            synchronized (indexWrites) {
                client.indices().refresh(r -> r.index(next));
                long indexed = client.count(c -> c.index(next)).count();
                long database = posts.selectCount(
                    Wrappers.<PostEntity>lambdaQuery().eq(PostEntity::getReviewState, "NORMAL")
                );
                LOG.info("Index rebuild counts: documents={}, posts={}", indexed, database);
                if (
                    indexed != database || !token.equals(redis.opsForValue().get(lock))
                ) throw new IllegalStateException("Index rebuild validation failed");
                client.indices().updateAliases(a -> {
                    for (String old : aliases)
                        a.actions(action -> action.remove(r -> r.index(old).alias(ALIAS)));
                    a.actions(action -> action.add(v -> v.index(next).alias(ALIAS)));
                    return a;
                });
                redis.delete(ROOT + "building");
                for (var old : aliases)
                    if (!old.equals(next)) client.indices().delete(d -> d.index(old));
                retryAfter.set(0);
            }
            return true;
        } catch (Exception e) {
            LOG.warn("Index rebuild failed; current alias unchanged");
            return false;
        } finally {
            if (locked) try {
                redis.execute(UNLOCK, List.of(lock), token);
            } catch (RuntimeException e) {
                LOG.warn("Index unlock failed");
            }
        }
    }
}

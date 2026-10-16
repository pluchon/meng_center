package com.pluchon.meng.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.content.entity.CommentEntity;
import com.pluchon.meng.content.entity.CommentImageEntity;
import com.pluchon.meng.content.entity.PostEntity;
import com.pluchon.meng.content.entity.PostImageEntity;
import com.pluchon.meng.content.entity.event.PostReadModelEvent;
import com.pluchon.meng.content.mapper.CommentImageMapper;
import com.pluchon.meng.content.mapper.PostImageMapper;
import com.pluchon.meng.content.mapper.PostMapper;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

// 内容分析请求与审核通知的事务发件箱
@Service
public class AiEventService {

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 帖子数据与内容版本
    @Autowired
    private PostMapper posts;

    // 配图归属与上传处理
    @Autowired
    private PostImageMapper images;

    // 评论配图数据
    @Autowired
    private CommentImageMapper commentImages;

    // 本域图片存储
    @Autowired
    private ImageStorage storage;

    // 帖子标签查询与归一化
    @Autowired
    private TagService tags;

    // 正文纯文本转换
    @Autowired
    private PostSummary summary;

    // 持久事件发布与确认
    @Autowired
    private RabbitTemplate rabbit;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 有界异步执行器
    @Autowired
    @Qualifier("contentIndexExecutor")
    private ThreadPoolTaskExecutor executor;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    // 避免同进程重复扫描待发事件
    private final java.util.concurrent.atomic.AtomicBoolean sending =
        new java.util.concurrent.atomic.AtomicBoolean();
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(
        AiEventService.class
    );

    // 读取完整帖子快照并保存待分析事件
    public void post(long id, String type, boolean includeImages) {
        PostEntity post = posts.selectById(id);
        if (post == null) return;
        var body = new LinkedHashMap<String, Object>();
        body.put("postId", id);
        body.put("authorId", post.getAuthorId());
        body.put("version", post.getUpdateTime().toString());
        body.put("category", post.getCategory().name());
        body.put("title", post.getTitle());
        body.put("plainText", summary.plain(post.getContent()));
        body.put("tags", tags.forPost(id));
        var thumbs = new ArrayList<Map<String, Object>>();
        if (includeImages) for (var image : images.selectList(
            Wrappers.<PostImageEntity>lambdaQuery().eq(PostImageEntity::getPostId, id)
        ))
            if (!Boolean.TRUE.equals(image.getAiGenerated())) thumbs.add(
                thumbnail(image.getId(), image.getThumbName())
            );
        body.put("imageThumbs", thumbs);
        body.put("imagesChanged", includeImages);
        enqueue(type, body);
    }

    // 对比当前配图与已保存的逐图审核结果
    public boolean needsImageReview(long id) {
        var feature = jdbc.queryForList(
            "SELECT image_reviews FROM post_ai_feature WHERE post_id=? AND delete_state=0",
            id
        );
        if (feature.isEmpty()) return true;
        var audited = new java.util.HashSet<Long>();
        for (var node : json.readTree(feature.get(0).get("image_reviews").toString()))
            audited.add(node.path("imageId").asLong());
        var current = images
            .selectList(Wrappers.<PostImageEntity>lambdaQuery().eq(PostImageEntity::getPostId, id))
            .stream()
            .filter(image -> !Boolean.TRUE.equals(image.getAiGenerated()))
            .map(PostImageEntity::getId)
            .collect(java.util.stream.Collectors.toSet());
        return !audited.equals(current);
    }

    // 保存评论全文与审核小图事件
    public void comment(CommentEntity comment, Map<String, Object> body) {
        var payload = new LinkedHashMap<>(body);
        payload.put("content", comment.getContent());
        var thumbs = new ArrayList<Map<String, Object>>();
        for (var image : commentImages.selectList(
            Wrappers.<CommentImageEntity>lambdaQuery().eq(
                CommentImageEntity::getCommentId,
                comment.getId()
            )
        ))
            thumbs.add(thumbnail(image.getId(), image.getThumbName()));
        payload.put("imageThumbs", thumbs);
        enqueue("comment.created", payload);
    }

    // 将本地图片缩成去元数据的 JPEG 小图
    private Map<String, Object> thumbnail(long id, String name) {
        try {
            var source = ImageIO.read(storage.path(name).toFile());
            if (source == null) throw new IllegalStateException("Missing image");
            double scale = Math.min(1, 512d / Math.max(source.getWidth(), source.getHeight()));
            var target = new BufferedImage(
                Math.max(1, (int) (source.getWidth() * scale)),
                Math.max(1, (int) (source.getHeight() * scale)),
                BufferedImage.TYPE_INT_RGB
            );
            var graphics = target.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
                graphics.drawImage(source, 0, 0, target.getWidth(), target.getHeight(), null);
            } finally {
                graphics.dispose();
            }
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(target, "jpeg", bytes);
            return Map.of(
                "id",
                id,
                "data",
                Base64.getEncoder().encodeToString(bytes.toByteArray())
            );
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Review thumbnail unavailable", error);
        }
    }

    // 在当前业务事务中写入事件发件箱
    public void enqueue(String type, Map<String, Object> body) {
        String id = UUID.randomUUID().toString();
        var envelope = new EventEnvelope<>(id, type, clock.instant(), id, body);
        jdbc.update(
            "INSERT INTO ai_event_outbox(event_id,event_type,body) VALUES (?,?,?)",
            id,
            type,
            json.writeValueAsString(envelope)
        );
    }

    // 内容事务提交后唤起补发
    @TransactionalEventListener
    public void changed(PostReadModelEvent event) {
        dispatchAsync();
    }

    // 启动后扫描未发送事件
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        dispatchAsync();
    }

    // 异步提交补发任务，满载时交给定时任务
    public void dispatchAsync() {
        try {
            executor.execute(this::dispatch);
        } catch (org.springframework.core.task.TaskRejectedException ignored) {
            LOG.warn("AI outbox dispatch deferred to maintenance job");
        }
    }

    // 等待 broker 确认后标记已发送
    public void dispatch() {
        if (!sending.compareAndSet(false, true)) return;
        try {
            var rows = jdbc.queryForList(
                "SELECT id,event_type,body FROM ai_event_outbox WHERE sent=0 ORDER BY create_time LIMIT 100"
            );
            for (var row : rows) {
                try {
                    var message = new org.springframework.amqp.core.Message(
                        ((String) row.get("body")).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    );
                    message.getMessageProperties().setContentType("application/json");
                    message
                        .getMessageProperties()
                        .setDeliveryMode(
                            org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT
                        );
                    rabbit.invoke(ops -> {
                        ops.send("meng.content.topic", (String) row.get("event_type"), message);
                        ops.waitForConfirmsOrDie(3000);
                        return true;
                    });
                    jdbc.update("UPDATE ai_event_outbox SET sent=1 WHERE id=?", row.get("id"));
                } catch (RuntimeException error) {
                    LOG.warn("AI outbox deferred error={}", error.getClass().getSimpleName());
                    break;
                }
            }
        } finally {
            sending.set(false);
        }
    }
}

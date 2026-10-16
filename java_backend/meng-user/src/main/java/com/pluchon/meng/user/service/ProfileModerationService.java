package com.pluchon.meng.user.service;

import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.common.web.context.UserContext;
import com.pluchon.meng.user.entity.UserEntity;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

// 公开资料审核、原值保留与版本安全恢复
@Service
public class ProfileModerationService {

    // 仅访问本域数据库
    @Autowired
    private JdbcTemplate jdbc;

    // 持久事件发布与确认
    @Autowired
    private RabbitTemplate rabbit;

    // 事务内领域事件发布
    @Autowired
    private ApplicationEventPublisher events;

    // 可替换的业务时钟
    @Autowired
    private Clock clock;

    // 头像文件存储根目录
    @Value("${meng.user.avatar-root}")
    private String avatarRoot;

    // 强制隐藏开关，默认仅记录结论
    @Value("${meng.user.profile-moderation.enforce:false}")
    private boolean enforce;

    // 隔离验证实例关闭整条资料分析链路，避免竞争业务发件箱和任务端口。
    @Value("${meng.user.profile-moderation.enabled:true}")
    private boolean enabled = true;

    // 复用 JSON 编解码器
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();

    // 查询本人当前版本被清除的资料说明
    public Map<String, Object> latest() {
        var rows = jdbc.queryForList(
            "SELECT r.verdict,r.reason,r.review_state FROM profile_ai_review r JOIN user u ON u.id=r.user_id AND u.profile_version=r.profile_version WHERE r.user_id=? AND r.review_state='CLEARED' AND r.delete_state=0 ORDER BY r.id DESC LIMIT 1",
            UserContext.require().userId()
        );
        return rows.isEmpty() ? Map.of() : rows.get(0);
    }

    // 管理员读取待复核的资料结论
    public List<Map<String, Object>> pending() {
        if (
            !"ADMIN".equals(UserContext.require().role())
        ) throw new com.pluchon.meng.common.core.error.BusinessException(
            com.pluchon.meng.common.core.error.CommonErrorCode.FORBIDDEN
        );
        return jdbc.queryForList(
            "SELECT id,user_id,profile_version,verdict,labels,reason,original_nickname,original_signature,original_avatar,review_state FROM profile_ai_review WHERE verdict<>'PASS' AND delete_state=0 ORDER BY id DESC LIMIT 100"
        );
    }

    // 保留尚未处理或可能恢复的头像原文件
    public boolean retainAvatar(String name) {
        return (
            name != null &&
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM profile_ai_review WHERE original_avatar=? AND verdict<>'PASS' AND review_state IN ('UNHANDLED','CLEARED') AND delete_state=0",
                Long.class,
                name
            ) > 0
        );
    }

    public record Changed(long id) {}

    // 更新资料版本并保存完整审核快照
    public void updated(UserEntity user, boolean avatarChanged) {
        if (!enabled) return;
        jdbc.update(
            "UPDATE user SET profile_version=CURRENT_TIMESTAMP(6) WHERE id=?",
            user.getId()
        );
        var version = jdbc.queryForObject(
            "SELECT profile_version FROM user WHERE id=?",
            LocalDateTime.class,
            user.getId()
        );
        var body = new LinkedHashMap<String, Object>();
        body.put("userId", user.getId());
        body.put("version", version.toString());
        body.put("nickname", user.getNickname() == null ? "" : user.getNickname());
        body.put("signature", user.getBio() == null ? "" : user.getBio());
        boolean includeAvatar =
            user.getAvatar() != null &&
            (avatarChanged ||
                jdbc.queryForObject(
                    "SELECT COUNT(*) FROM profile_ai_review WHERE user_id=? AND original_avatar=? AND avatar_review IS NOT NULL AND delete_state=0",
                    Long.class,
                    user.getId(),
                    user.getAvatar()
                ) == 0);
        body.put("avatarChanged", includeAvatar || user.getAvatar() == null);
        if (includeAvatar) {
            try {
                var root = Path.of(avatarRoot).toAbsolutePath().normalize();
                var path = root.resolve(user.getAvatar()).normalize();
                if (!path.startsWith(root)) throw new IllegalArgumentException(
                    "Invalid avatar path"
                );
                var source = ImageIO.read(path.toFile());
                var image = new BufferedImage(
                    source.getWidth(),
                    source.getHeight(),
                    BufferedImage.TYPE_INT_RGB
                );
                var graphics = image.createGraphics();
                try {
                    graphics.setColor(java.awt.Color.WHITE);
                    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                    graphics.drawImage(source, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                var bytes = new ByteArrayOutputStream();
                ImageIO.write(image, "jpeg", bytes);
                body.put(
                    "avatarThumb",
                    Map.of(
                        "id",
                        user.getId(),
                        "data",
                        Base64.getEncoder().encodeToString(bytes.toByteArray())
                    )
                );
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Avatar review thumbnail unavailable", error);
            }
        }
        String id = UUID.randomUUID().toString();
        jdbc.update(
            "INSERT INTO profile_ai_outbox(event_id,body) VALUES (?,?)",
            id,
            json.writeValueAsString(
                new EventEnvelope<>(id, "user.profile-updated", clock.instant(), id, body)
            )
        );
        events.publishEvent(new Changed(user.getId()));
    }

    // 资料事务提交后补发审核请求
    @TransactionalEventListener
    public void committed(Changed event) {
        dispatch();
    }

    @org.springframework.context.event.EventListener(
        org.springframework.boot.context.event.ApplicationReadyEvent.class
    )
    // 启动后补发未送达的资料事件
    public void ready() {
        dispatch();
    }

    // 持久发布并确认后标记已发送
    public void dispatch() {
        if (!enabled) return;
        jdbc.update(
            "DELETE FROM profile_ai_outbox WHERE sent=1 AND create_time<?",
            java.sql.Timestamp.from(clock.instant().minus(Duration.ofDays(7)))
        );
        for (var row : jdbc.queryForList(
            "SELECT id,body FROM profile_ai_outbox WHERE sent=0 ORDER BY create_time LIMIT 50"
        ))
            try {
                var message = new org.springframework.amqp.core.Message(
                    row.get("body").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)
                );
                message.getMessageProperties().setContentType("application/json");
                message
                    .getMessageProperties()
                    .setDeliveryMode(org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                String route = json
                    .readTree(row.get("body").toString())
                    .path("eventType")
                    .asString();
                rabbit.invoke(ops -> {
                    ops.send("meng.user.topic", route, message);
                    ops.waitForConfirmsOrDie(3000);
                    return true;
                });
                jdbc.update("UPDATE profile_ai_outbox SET sent=1 WHERE id=?", row.get("id"));
            } catch (RuntimeException error) {
                org.slf4j.LoggerFactory.getLogger(ProfileModerationService.class).warn(
                    "Profile review dispatch deferred"
                );
                break;
            }
    }

    // 丢弃旧版本结果并沿用未变化头像的结论
    @Transactional(rollbackFor = Exception.class)
    public void result(Map<String, Object> body) {
        long user = ((Number) body.get("userId")).longValue();
        String verdict = (String) body.get("verdict");
        if (
            !List.of("PASS", "SUSPECT", "BLOCK").contains(verdict)
        ) throw new IllegalArgumentException("Invalid profile verdict");
        var version = LocalDateTime.parse(body.get("version").toString());
        var rows = jdbc.queryForList(
            "SELECT nickname,bio,avatar,profile_version FROM user WHERE id=? AND delete_state=0 FOR UPDATE",
            user
        );
        if (rows.isEmpty()) return;
        var row = rows.get(0);
        Object storedVersion = row.get("profile_version");
        var actualVersion =
            storedVersion instanceof LocalDateTime time
                ? time
                : ((java.sql.Timestamp) storedVersion).toLocalDateTime();
        if (!actualVersion.equals(version)) return;
        Map<String, Object> avatarReview = null;
        if (Boolean.TRUE.equals(body.get("avatarChanged"))) {
            Object imageReviews = body.get("imageReviews");
            if (imageReviews instanceof List<?> reviews && !reviews.isEmpty()) avatarReview =
                json.convertValue(
                    reviews.get(0),
                    new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
                );
        } else if (row.get("avatar") != null) {
            var cached = jdbc.queryForList(
                "SELECT avatar_review FROM profile_ai_review WHERE user_id=? AND original_avatar=? AND avatar_review IS NOT NULL AND delete_state=0 ORDER BY id DESC LIMIT 1",
                user,
                row.get("avatar")
            );
            if (!cached.isEmpty()) avatarReview = json.readValue(
                cached.get(0).get("avatar_review").toString(),
                new tools.jackson.core.type.TypeReference<Map<String, Object>>() {}
            );
        }
        var labels = new LinkedHashSet<String>();
        if (body.get("labels") instanceof List<?> values) for (var value : values)
            labels.add(value.toString());
        String reason = Objects.toString(body.get("reason"), "");
        // 昵称和签名、头像分开判断：哪一部分违规只清哪一部分
        boolean textBlocked = "BLOCK".equals(verdict);
        String textReason = reason;
        boolean avatarBlocked = false;
        String avatarReason = "";
        var severity = Map.of("PASS", 0, "SUSPECT", 1, "BLOCK", 2);
        if (avatarReview != null) {
            String avatarVerdict = avatarReview.get("verdict").toString();
            if (!severity.containsKey(avatarVerdict)) throw new IllegalArgumentException(
                "Invalid avatar verdict"
            );
            avatarBlocked = "BLOCK".equals(avatarVerdict);
            avatarReason = Objects.toString(avatarReview.get("reason"), "");
            if (severity.get(avatarVerdict) > severity.get(verdict)) {
                verdict = avatarVerdict;
                reason = avatarReason;
            }
            if (avatarReview.get("labels") instanceof List<?> values) for (var value : values)
                labels.add("image:" + user + ":" + value);
        }
        int inserted = jdbc.update(
            "INSERT IGNORE INTO profile_ai_review(user_id,profile_version,verdict,labels,reason,model_version,original_nickname,original_signature,original_avatar,avatar_review) VALUES (?,?,?,?,?,?,?,?,?,?)",
            user,
            version,
            verdict,
            json.writeValueAsString(labels),
            reason,
            body.get("modelVersion"),
            row.get("nickname"),
            row.get("bio"),
            row.get("avatar"),
            avatarReview == null ? null : json.writeValueAsString(avatarReview)
        );
        if (inserted == 0) return;
        if (enforce && (textBlocked || avatarBlocked)) {
            var cleared = new java.util.ArrayList<String>();
            if (textBlocked) cleared.addAll(List.of("nickname", "signature"));
            if (avatarBlocked) cleared.add("avatar");
            jdbc.update(
                "UPDATE user SET nickname=IF(?,NULL,nickname),bio=IF(?,NULL,bio),avatar=IF(?,NULL,avatar) WHERE id=?",
                textBlocked,
                textBlocked,
                avatarBlocked,
                user
            );
            jdbc.update(
                "UPDATE profile_ai_review SET review_state='CLEARED' WHERE user_id=? AND profile_version=?",
                user,
                version
            );
            publish(user, cleared, labels, shortReason(textBlocked ? textReason : avatarReason));
        }
    }

    // 通知里的理由只留一句话，最多 80 个字
    private String shortReason(String reason) {
        if (reason == null) return "";
        String text = reason.strip().replaceAll("\s+", " ");
        return text.codePointCount(0, text.length()) <= 80
            ? text
            : text.substring(0, text.offsetByCodePoints(0, 80)) + "…";
    }

    public record Notification(EventEnvelope<Map<String, Object>> envelope) {}

    // 将本人审核通知保存到事务发件箱
    private void publish(long user, List<String> fields, Object labels, String reason) {
        String id = UUID.randomUUID().toString();
        var envelope = new EventEnvelope<>(
            id,
            "user.profile-review-changed",
            clock.instant(),
            id,
            Map.<String, Object>of(
                "userId",
                user,
                "clearedFields",
                fields,
                "labels",
                labels,
                "reason",
                reason
            )
        );
        jdbc.update(
            "INSERT INTO profile_ai_outbox(event_id,body) VALUES (?,?)",
            id,
            json.writeValueAsString(envelope)
        );
        events.publishEvent(new Notification(envelope));
    }

    // 审核事务提交后补发通知
    @TransactionalEventListener
    public void notify(Notification event) {
        dispatch();
    }

    // 仅在用户尚未再修改资料时恢复原值
    @Transactional(rollbackFor = Exception.class)
    public void restore(long id) {
        if (
            !"ADMIN".equals(UserContext.require().role())
        ) throw new com.pluchon.meng.common.core.error.BusinessException(
            com.pluchon.meng.common.core.error.CommonErrorCode.FORBIDDEN
        );
        var rows = jdbc.queryForList(
            "SELECT * FROM profile_ai_review WHERE id=? AND delete_state=0 FOR UPDATE",
            id
        );
        if (rows.isEmpty()) throw new com.pluchon.meng.common.core.error.BusinessException(
            com.pluchon.meng.common.core.error.CommonErrorCode.NOT_FOUND
        );
        var row = rows.get(0);
        if (!"CLEARED".equals(row.get("review_state"))) return;
        int restored = jdbc.update(
            "UPDATE user SET nickname=?,bio=?,avatar=?,profile_version=CURRENT_TIMESTAMP(6) WHERE id=? AND profile_version=? AND delete_state=0",
            row.get("original_nickname"),
            row.get("original_signature"),
            row.get("original_avatar"),
            row.get("user_id"),
            row.get("profile_version")
        );
        if (restored == 0) throw new com.pluchon.meng.common.core.error.BusinessException(
            com.pluchon.meng.common.core.error.CommonErrorCode.CONFLICT
        );
        jdbc.update(
            "UPDATE profile_ai_review SET review_state='RESTORED',avatar_review=? WHERE id=?",
            json.writeValueAsString(
                Map.of("verdict", "PASS", "labels", List.of(), "reason", "人工复核通过")
            ),
            id
        );
        publish(((Number) row.get("user_id")).longValue(), List.of(), List.of(), "");
    }
}

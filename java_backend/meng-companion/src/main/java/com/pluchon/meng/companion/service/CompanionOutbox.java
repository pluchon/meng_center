package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import com.pluchon.meng.companion.mapper.RuntimeMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

// 发件箱与业务结果同事务；只有收到 broker confirm 且没有退回消息才标记发送成功。
@Service
public class CompanionOutbox {

    @Autowired
    private RuntimeMapper runtime;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private Clock clock;

    @Autowired
    private WorkStorage storage;

    @Autowired
    private CompanionMapper mapper;

    @Autowired
    private CompanionLimits limits;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private ThreadPoolTaskExecutor companionBackgroundExecutor;

    private final JsonMapper json = JsonMapper.builder()
        .findAndAddModules()
        .build();
    private static final org.slf4j.Logger LOG =
        org.slf4j.LoggerFactory.getLogger(CompanionOutbox.class);

    public long sessionOwner(long session) {
        var rows = runtime.sessionOwnerRows(session);
        return rows.isEmpty()
            ? 0
            : ((Number) rows.get(0).get("user_id")).longValue();
    }

    public String thumbnail(String file) {
        return storage.thumbnail(file);
    }

    public void sticker(long id, String sticker) {
        runtime.sticker(sticker, id);
    }

    public void enqueue(String type, String id, Object body) {
        runtime.insertOutbox(id, type, json.writeValueAsString(body));
        if (
            TransactionSynchronizationManager.isSynchronizationActive()
        ) TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    wake();
                }
            }
        );
        else wake();
    }

    private void wake() {
        try {
            companionBackgroundExecutor.execute(this::dispatch);
        } catch (RuntimeException ignored) {}
    }

    public void dispatch() {
        String token = UUID.randomUUID().toString();
        if (
            !Boolean.TRUE.equals(
                redis
                    .opsForValue()
                    .setIfAbsent(
                        "meng:companion:outbox-lock",
                        token,
                        Duration.ofSeconds(55)
                    )
            )
        ) return;
        try {
            long deadline =
                System.nanoTime() +
                java.util.concurrent.TimeUnit.SECONDS.toNanos(50);
            var pending = runtime.pendingOutbox();
            for (var row : pending) {
                if (System.nanoTime() > deadline) break;
                try {
                    String type = row.get("event_type").toString(),
                        id = row.get("event_id").toString();
                    var body = json.readTree(row.get("payload").toString());
                    String route = type;
                    if ("turn.committed".equals(type)) route +=
                        "." +
                        Math.floorMod(
                            Objects.hash(
                                body.path("userId").asLong(),
                                body.path("characterCode").asString()
                            ),
                            8
                        );
                    var envelope = new EventEnvelope<>(
                        id,
                        type,
                        clock.instant(),
                        id,
                        body
                    );
                    var properties = new MessageProperties();
                    properties.setContentType(
                        MessageProperties.CONTENT_TYPE_JSON
                    );
                    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    var correlation = new CorrelationData(id);
                    rabbit.setMandatory(true);
                    rabbit.send(
                        "meng.companion.topic",
                        route,
                        new Message(
                            json.writeValueAsBytes(envelope),
                            properties
                        ),
                        correlation
                    );
                    var confirm = correlation
                        .getFuture()
                        .get(3, TimeUnit.SECONDS);
                    if (
                        !confirm.isAck() || correlation.getReturned() != null
                    ) throw new IllegalStateException(
                        "Companion event was not routed"
                    );
                    runtime.sentOutbox(((Number) row.get("id")).longValue());
                } catch (Exception error) {
                    if (
                        error instanceof InterruptedException
                    ) Thread.currentThread().interrupt();
                    runtime.retryOutbox(((Number) row.get("id")).longValue());
                    LOG.warn(
                        "Companion outbox deferred error={}",
                        error.getClass().getSimpleName()
                    );
                    break;
                }
            }
        } finally {
            redis.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<>(
                    "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0",
                    Long.class
                ),
                List.of("meng:companion:outbox-lock"),
                token
            );
        }
    }

    public void drawing(
        long user,
        long session,
        long message,
        String instruction,
        CompanionLimits.Reservation reservation,
        long revision
    ) {
        if (
            instruction.isBlank() || instruction.length() > 2000
        ) throw new IllegalArgumentException("Invalid drawing instruction");
        var image = new CompanionLimits.Reservation(
            user,
            reservation.date(),
            reservation.token(),
            List.of("image"),
            false
        );
        runtime.insertDrawing(
            user,
            session,
            message,
            instruction,
            json.writeValueAsString(image),
            revision
        );
        enqueue(
            "drawing.requested",
            "drawing-" + message,
            Map.of("messageId", message)
        );
    }

    public void checkCooldown(
        long user,
        String code,
        long message,
        LocalDate date
    ) {
        var bond = mapper.bond(user, code);
        var start = date
            .atStartOfDay(ZoneId.of(bond.getTimeZone()))
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime();
        if (mapper.blocks(user, start, start.plusDays(1)) >= 3) {
            limits.cooldown(user);
            mapper.safety(user, code, message, "COOLDOWN");
        }
    }

    public void touch(long user, String code, List<Long> ids) {
        for (long id : ids) runtime.touchMemory(id, user, code);
        if (!ids.isEmpty()) redis.delete(
            "meng:companion:memo:" + user + ":" + code
        );
    }

    public boolean feedback(
        long user,
        long message,
        String kind,
        String rejected
    ) {
        return runtime.insertFeedback(user, message, kind, rejected) > 0;
    }

    public boolean regenerated(long user, long turn) {
        return runtime.regenerationCount(user, turn) > 0;
    }

    public void replace(
        long user,
        long rejected,
        long newTurn,
        long replacement
    ) {
        var previous = runtime.rejectedTurn(rejected);
        runtime.hideCharacterTurn(previous);
        runtime.hideUserTurn(newTurn);
        runtime.markRegeneration(rejected, newTurn);
        runtime.feedbackReplacement(replacement, user, rejected);
    }

    public void messageContext(long turn, Object ids) {
        runtime.messageMemories(json.writeValueAsString(ids), turn);
    }

    public void mentioned(long user, String code, long id) {
        runtime.mentionMemory(id, user, code);
    }
}

package com.pluchon.meng.companion.service;

import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.companion.entity.Rows;
import com.pluchon.meng.companion.mapper.CompanionMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import tools.jackson.databind.json.JsonMapper;

// 发件箱与业务结果同事务；只有收到 broker confirm 且没有退回消息才标记发送成功。
@Service
public class CompanionOutbox {

    @Autowired
    private JdbcTemplate jdbc;

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
        var rows = jdbc.queryForList(
            "SELECT user_id FROM companion_session WHERE id=? AND delete_state=0",
            session
        );
        return rows.isEmpty()
            ? 0
            : ((Number) rows.get(0).get("user_id")).longValue();
    }

    public String thumbnail(String file) {
        return storage.thumbnail(file);
    }

    public void sticker(long id, String sticker) {
        jdbc.update(
            "UPDATE companion_message SET kind='STICKER',sticker=? WHERE id=?",
            sticker,
            id
        );
    }

    public void enqueue(String type, String id, Object body) {
        jdbc.update(
            "INSERT IGNORE INTO companion_outbox(event_id,event_type,payload) VALUES(?,?,?)",
            id,
            type,
            json.writeValueAsString(body)
        );
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
            var pending = jdbc.queryForList(
                "SELECT * FROM companion_outbox WHERE status='PENDING' AND delete_state=0 ORDER BY id LIMIT 100"
            );
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
                    jdbc.update(
                        "UPDATE companion_outbox SET status='SENT' WHERE id=?",
                        row.get("id")
                    );
                } catch (Exception error) {
                    if (
                        error instanceof InterruptedException
                    ) Thread.currentThread().interrupt();
                    jdbc.update(
                        "UPDATE companion_outbox SET retry_count=retry_count+1 WHERE id=?",
                        row.get("id")
                    );
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
        jdbc.update(
            "INSERT INTO companion_drawing(user_id,session_id,message_id,instruction,reservation,revision) VALUES(?,?,?,?,?,?)",
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
        for (long id : ids)
            jdbc.update(
                "UPDATE companion_memory SET last_used_time=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND character_code=? AND invalid_at IS NULL AND delete_state=0",
                id,
                user,
                code
            );
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
        return (
            jdbc.update(
                "INSERT IGNORE INTO companion_feedback(user_id,message_id,kind,rejected_content) VALUES(?,?,?,?)",
                user,
                message,
                kind,
                rejected
            ) > 0
        );
    }

    public boolean regenerated(long user, long turn) {
        return (
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM companion_feedback f JOIN companion_message m ON m.id=f.message_id WHERE f.user_id=? AND f.kind='REGENERATE' AND m.turn_id=?",
                Integer.class,
                user,
                turn
            ) > 0
        );
    }

    public void replace(
        long user,
        long rejected,
        long newTurn,
        long replacement
    ) {
        var previous = jdbc.queryForObject(
            "SELECT turn_id FROM companion_message WHERE id=?",
            Long.class,
            rejected
        );
        jdbc.update(
            "UPDATE companion_message SET delete_state=1 WHERE turn_id=? AND role='CHARACTER' AND work_id IS NULL",
            previous
        );
        jdbc.update(
            "UPDATE companion_message SET delete_state=1 WHERE turn_id=? AND role='USER'",
            newTurn
        );
        jdbc.update(
            "UPDATE companion_message SET regenerates_message_id=? WHERE turn_id=? AND role='CHARACTER'",
            rejected,
            newTurn
        );
        jdbc.update(
            "UPDATE companion_feedback SET replacement_message_id=? WHERE user_id=? AND message_id=? AND kind='REGENERATE'",
            replacement,
            user,
            rejected
        );
    }

    public void messageContext(long turn, Object ids) {
        jdbc.update(
            "UPDATE companion_message SET memory_ids=? WHERE turn_id=? AND role='CHARACTER'",
            json.writeValueAsString(ids),
            turn
        );
    }

    public void mentioned(long user, String code, long id) {
        jdbc.update(
            "UPDATE companion_memory SET mentioned_at=UTC_TIMESTAMP(6) WHERE id=? AND user_id=? AND character_code=? AND invalid_at IS NULL AND delete_state=0",
            id,
            user,
            code
        );
    }
}

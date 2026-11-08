package com.pluchon.meng.growth.mq;

import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.growth.entity.LevelOutboxRow;
import com.pluchon.meng.growth.mapper.LevelOutboxMapper;
import jakarta.annotation.PreDestroy;
import java.time.ZoneOffset;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

// 等级事件发件箱的投递：升级事务提交后马上发一次，另有定时补发；收到 broker 确认才标记已发送
@Component
public class LevelEventPublisher {

    // 每批最多投递的条数
    private static final int BATCH = 100;

    // 补发间隔（秒）
    private static final long RETRY_SECONDS = 30;

    // 投递失败的日志
    private static final Logger LOG = LoggerFactory.getLogger(LevelEventPublisher.class);

    // 领域事件发送组件
    @Autowired
    private RabbitTemplate rabbit;

    // 发件箱存取
    @Autowired
    private LevelOutboxMapper outbox;

    // 领取与标记在同一个短事务里，行锁随提交释放
    @Autowired
    private TransactionTemplate transactions;

    // 本实例同一时间只跑一轮投递
    private final AtomicBoolean sending = new AtomicBoolean();

    // 补发线程
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
        var thread = new Thread(task, "growth-level-outbox");
        thread.setDaemon(true);
        return thread;
    });

    // 启动后先补发积压，再定时扫描
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        timer.scheduleWithFixedDelay(this::dispatch, 0, RETRY_SECONDS, TimeUnit.SECONDS);
    }

    // 升级事务提交后唤起投递，不占用请求线程
    @TransactionalEventListener
    public void committed(LevelChangedEvent changed) {
        timer.execute(this::dispatch);
    }

    // 分批领取未发送的事件并逐条投递，遇到失败就停下等下一轮
    void dispatch() {
        if (!sending.compareAndSet(false, true)) return;
        try {
            boolean more = true;
            while (more) more = Boolean.TRUE.equals(transactions.execute(status -> deliverBatch()));
        } catch (RuntimeException unavailable) {
            LOG.warn("Level outbox deferred: {}", unavailable.getClass().getSimpleName());
        } finally {
            sending.set(false);
        }
    }

    // 投递一批，返回是否还可能有剩余；已确认的在本批提交时一起标记
    private boolean deliverBatch() {
        var rows = outbox.claimPending(BATCH);
        for (var row : rows) {
            try {
                send(row);
            } catch (RuntimeException unavailable) {
                LOG.warn("Level event {} deferred: {}", row.getEventId(), unavailable.getClass().getSimpleName());
                return false;
            }
            outbox.markSent(row.getId());
        }
        return rows.size() == BATCH;
    }

    // 沿用原有的事件信封和编号，消费方按事件编号去重
    private void send(LevelOutboxRow row) {
        var envelope = new EventEnvelope<>(
            row.getEventId(),
            LevelChangedEvent.EVENT_TYPE,
            row.getOccurredAt().toInstant(ZoneOffset.UTC),
            row.getEventId(),
            new LevelChangedEvent(row.getUserId(), row.getLevel())
        );
        rabbit.invoke(operations -> {
            operations.convertAndSend(LevelChangedEvent.EXCHANGE, LevelChangedEvent.EVENT_TYPE, envelope);
            operations.waitForConfirmsOrDie(3000);
            return true;
        });
    }

    @PreDestroy
    public void stop() {
        timer.shutdownNow();
    }
}

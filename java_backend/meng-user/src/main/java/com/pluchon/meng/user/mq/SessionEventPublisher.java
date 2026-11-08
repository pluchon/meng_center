package com.pluchon.meng.user.mq;

import com.pluchon.meng.api.user.event.SessionsRevokedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// 会话撤销后对外广播；在事务里撤销的（改密、重置）等提交后再发，回滚的不发
@Component
public class SessionEventPublisher {

    // 事件发送失败的日志
    private static final Logger LOG = LoggerFactory.getLogger(SessionEventPublisher.class);

    // 领域事件发送组件
    @Autowired
    private RabbitTemplate rabbit;

    // 业务时钟
    @Autowired
    private Clock clock;

    // 发不出去只记错误日志：会话已在 Redis 撤销，长连接还有最长存活时间兜底
    @TransactionalEventListener(fallbackExecution = true)
    public void revoked(SessionsRevokedEvent revoked) {
        String id = UUID.randomUUID().toString();
        try {
            rabbit.convertAndSend(
                SessionsRevokedEvent.EXCHANGE,
                SessionsRevokedEvent.EVENT_TYPE,
                new EventEnvelope<>(id, SessionsRevokedEvent.EVENT_TYPE, clock.instant(), id, revoked)
            );
        } catch (AmqpException unavailable) {
            LOG.error("Sessions revoked event {} not published", id, unavailable);
        }
    }
}

package com.pluchon.meng.im.mq;

import com.pluchon.meng.api.user.event.SessionsRevokedEvent;
import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.im.realtime.ImSocket;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

// 会话撤销后断开对应的实时连接；连接只在本实例内存里，所以每个实例各绑一条匿名队列，都能收到
@Component
public class SessionRevocationListener {

    // 实时连接
    @Autowired
    private ImSocket socket;

    @RabbitListener(
        bindings = @QueueBinding(
            value = @Queue,
            exchange = @Exchange(value = SessionsRevokedEvent.EXCHANGE, type = ExchangeTypes.TOPIC),
            key = SessionsRevokedEvent.EVENT_TYPE
        )
    )
    public void revoked(EventEnvelope<SessionsRevokedEvent> event) {
        if (
            !SessionsRevokedEvent.EVENT_TYPE.equals(event.eventType()) || event.body() == null
        ) throw new IllegalArgumentException("Wrong event envelope");
        var revoked = event.body();
        socket.revoke(revoked.userId(), revoked::covers);
    }
}

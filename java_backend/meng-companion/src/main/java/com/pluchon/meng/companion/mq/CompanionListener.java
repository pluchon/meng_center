package com.pluchon.meng.companion.mq;

import com.pluchon.meng.common.core.event.EventEnvelope;
import com.pluchon.meng.companion.service.CompanionBackground;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

// 每个关系分区只有一个消费线程，按提交顺序整理，失败使用统一的有限重试与死信策略。
@Component
public class CompanionListener {

    @Autowired
    private CompanionBackground background;

    @RabbitListeners({
        @RabbitListener(queues = "meng.companion.turns.0", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.1", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.2", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.3", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.4", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.5", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.6", concurrency = "1"),
        @RabbitListener(queues = "meng.companion.turns.7", concurrency = "1"),
    })
    public void turn(EventEnvelope<JsonNode> event) {
        if (
            !"turn.committed".equals(event.eventType())
        ) throw new IllegalArgumentException("Wrong companion event");
        background.consolidate(event);
    }

    @RabbitListener(queues = "meng.companion.drawings", concurrency = "2")
    public void drawing(EventEnvelope<JsonNode> event) {
        if (
            !"drawing.requested".equals(event.eventType())
        ) throw new IllegalArgumentException("Wrong drawing event");
        background.draw(event);
    }
}

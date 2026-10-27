package com.pluchon.meng.companion.config;

import java.util.ArrayList;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

// 同一关系固定分区，每个分区单消费者保证提交顺序；有限重试后进入本域死信队列。
@Configuration
public class CompanionMqConfiguration {

    @Bean
    Declarables companionQueues() {
        var all = new ArrayList<Declarable>();
        var exchange = new TopicExchange("meng.companion.topic", true, false);
        var dead = new FanoutExchange("meng.companion.dead", true, false);
        var deadQueue = QueueBuilder.durable("meng.companion.dead").build();
        all.add(exchange);
        all.add(dead);
        all.add(deadQueue);
        all.add(BindingBuilder.bind(deadQueue).to(dead));
        for (int i = 0; i < 8; i++) {
            var queue = QueueBuilder.durable("meng.companion.turns." + i)
                .singleActiveConsumer()
                .deadLetterExchange("meng.companion.dead")
                .build();
            all.add(queue);
            all.add(
                BindingBuilder.bind(queue)
                    .to(exchange)
                    .with("turn.committed." + i)
            );
        }
        return new Declarables(all);
    }
}

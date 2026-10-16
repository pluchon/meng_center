package com.pluchon.meng.content.config;

import com.pluchon.meng.api.content.event.PostPublishedEvent;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 领域消息交换机、队列和路由配置
@Configuration
public class MqConfiguration {

    @Bean
    org.springframework.amqp.core.Declarables aiQueues() {
        var declarables = new java.util.ArrayList<org.springframework.amqp.core.Declarable>();
        var exchange = new TopicExchange("meng.ai.topic", true, false);
        declarables.add(exchange);
        for (String type : java.util.List.of(
            "post.published",
            "post.updated",
            "post.analysis-requested",
            "comment.created"
        )) {
            var queue = org.springframework.amqp.core.QueueBuilder.durable("meng.ai." + type)
                .deadLetterExchange("meng.ai.topic")
                .deadLetterRoutingKey(type + ".dead")
                .build();
            declarables.add(queue);
            declarables.add(
                org.springframework.amqp.core.BindingBuilder.bind(queue)
                    .to(new TopicExchange("meng.content.topic", true, false))
                    .with(type)
            );
        }
        for (String type : java.util.List.of("post.analyzed", "comment.moderated")) {
            var queue = org.springframework.amqp.core.QueueBuilder.durable("meng.content." + type)
                .deadLetterExchange("meng.ai.topic")
                .deadLetterRoutingKey("content." + type + ".dead")
                .build();
            var dead = org.springframework.amqp.core.QueueBuilder.durable(
                "meng.content." + type + ".dead"
            ).build();
            declarables.add(queue);
            declarables.add(dead);
            declarables.add(
                org.springframework.amqp.core.BindingBuilder.bind(queue).to(exchange).with(type)
            );
            declarables.add(
                org.springframework.amqp.core.BindingBuilder.bind(dead)
                    .to(exchange)
                    .with("content." + type + ".dead")
            );
        }
        return new org.springframework.amqp.core.Declarables(declarables);
    }

    @Bean
    TopicExchange contentExchange() {
        return new TopicExchange(PostPublishedEvent.EXCHANGE, true, false);
    }
}

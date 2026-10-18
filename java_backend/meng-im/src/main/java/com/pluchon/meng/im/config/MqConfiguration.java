package com.pluchon.meng.im.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 每种事件独立队列，共用有限重试与死信队列
@Configuration
public class MqConfiguration {

    @Bean
    Declarables notifications() {
        var all = new ArrayList<Declarable>();
        var dead = new FanoutExchange("meng.im.dead", true, false);
        var deadQueue = QueueBuilder.durable("meng.im.dead").build();
        all.add(dead);
        all.add(deadQueue);
        all.add(BindingBuilder.bind(deadQueue).to(dead));
        for (var event : List.of(
            "comment.created",
            "post.liked",
            "post.favorited",
            "comment.liked",
            "growth.level-changed",
            "user.approved",
            "user.followed",
            "post.review-changed",
            "comment.review-changed",
            "user.profile-review-changed",
            "companion.stage-reached",
            "companion.promise-due",
            "companion.drawing-finished"
        )) {
            var exchange = new TopicExchange(
                "meng." +
                    (event.startsWith("companion.")
                        ? "companion"
                        : event.startsWith("growth.")
                          ? "growth"
                          : event.startsWith("user.")
                            ? "user"
                            : "content") +
                    ".topic",
                true,
                false
            );
            var queue = QueueBuilder.durable("meng.im." + event)
                .deadLetterExchange("meng.im.dead")
                .build();
            all.add(exchange);
            all.add(queue);
            all.add(BindingBuilder.bind(queue).to(exchange).with(event));
        }
        return new Declarables(all);
    }
}

package com.pluchon.meng.growth.config;

import com.pluchon.meng.api.content.event.*;
import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.growth.mq.GrowthEventListener;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class MqConfiguration {

    @Bean
    Declarables events() {
        var content = new TopicExchange(
            PostPublishedEvent.EXCHANGE,
            true,
            false
        );
        var growth = new TopicExchange(LevelChangedEvent.EXCHANGE, true, false);
        var dead = new FanoutExchange("meng.growth.dead", true, false);
        var dq = QueueBuilder.durable("meng.growth.dead").build();
        var posts = QueueBuilder.durable(GrowthEventListener.POST_QUEUE)
            .deadLetterExchange(dead.getName())
            .build();
        var comments = QueueBuilder.durable(GrowthEventListener.COMMENT_QUEUE)
            .deadLetterExchange(dead.getName())
            .build();
        return new Declarables(
            content,
            growth,
            dead,
            dq,
            posts,
            comments,
            BindingBuilder.bind(dq).to(dead),
            BindingBuilder.bind(posts)
                .to(content)
                .with(PostPublishedEvent.EVENT_TYPE),
            BindingBuilder.bind(comments)
                .to(content)
                .with(CommentCreatedEvent.EVENT_TYPE)
        );
    }
}

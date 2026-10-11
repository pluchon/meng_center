package com.pluchon.meng.growth.config;

import com.pluchon.meng.api.content.event.CommentCreatedEvent;
import com.pluchon.meng.api.content.event.PostPublishedEvent;
import com.pluchon.meng.api.growth.event.LevelChangedEvent;
import com.pluchon.meng.growth.mq.GrowthEventListener;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 领域消息交换机、队列和路由配置
@Configuration
public class MqConfiguration {

    @Bean
    Declarables events() {
        var content = new TopicExchange(PostPublishedEvent.EXCHANGE, true, false);
        var growth = new TopicExchange(LevelChangedEvent.EXCHANGE, true, false);
        var dead = new FanoutExchange("meng.growth.dead", true, false);
        var dq = QueueBuilder.durable("meng.growth.dead").build();
        var posts =
                QueueBuilder.durable(GrowthEventListener.POST_QUEUE)
                        .deadLetterExchange(dead.getName())
                        .build();
        var comments =
                QueueBuilder.durable(GrowthEventListener.COMMENT_QUEUE)
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
                BindingBuilder.bind(posts).to(content).with(PostPublishedEvent.EVENT_TYPE),
                BindingBuilder.bind(comments).to(content).with(CommentCreatedEvent.EVENT_TYPE));
    }
}

package com.pluchon.meng.user.config;

import com.pluchon.meng.api.user.event.UserApprovedEvent;
import com.pluchon.meng.user.mq.UserMqNames;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 消息队列拓扑：验证码队列、审核通知队列、死信队列和对外的事件交换机
@Configuration
public class MqConfiguration {

    @Bean
    Declarables profileReviews() {
        var ai = new TopicExchange("meng.ai.topic", true, false);
        var user = new TopicExchange("meng.user.topic", true, false);
        var request = QueueBuilder.durable("meng.ai.user.profile-updated")
            .deadLetterExchange("meng.ai.topic")
            .deadLetterRoutingKey("user.profile-updated.dead")
            .build();
        var result = QueueBuilder.durable("meng.user.user.profile-moderated")
            .deadLetterExchange(UserMqNames.INTERNAL_EXCHANGE)
            .deadLetterRoutingKey(UserMqNames.DEAD_ROUTE)
            .build();
        return new Declarables(
            ai,
            user,
            request,
            result,
            BindingBuilder.bind(request).to(user).with("user.profile-updated"),
            BindingBuilder.bind(result).to(ai).with("user.profile-moderated")
        );
    }

    @Bean
    JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    Declarables growthEvents() {
        var e = new TopicExchange(
            com.pluchon.meng.api.growth.event.LevelChangedEvent.EXCHANGE,
            true,
            false
        );
        var q = QueueBuilder.durable(com.pluchon.meng.user.mq.LevelEventListener.QUEUE)
            .deadLetterExchange(UserMqNames.INTERNAL_EXCHANGE)
            .deadLetterRoutingKey(UserMqNames.DEAD_ROUTE)
            .build();
        return new Declarables(
            e,
            q,
            BindingBuilder.bind(q)
                .to(e)
                .with(com.pluchon.meng.api.growth.event.LevelChangedEvent.EVENT_TYPE)
        );
    }

    @Bean
    Declarables queues() {
        var exchange = new DirectExchange(UserMqNames.INTERNAL_EXCHANGE, true, false);
        var codes = QueueBuilder.durable(UserMqNames.CODE_QUEUE)
            .deadLetterExchange(UserMqNames.INTERNAL_EXCHANGE)
            .deadLetterRoutingKey(UserMqNames.DEAD_ROUTE)
            .build();
        var notifications = QueueBuilder.durable(UserMqNames.NOTIFICATION_QUEUE)
            .deadLetterExchange(UserMqNames.INTERNAL_EXCHANGE)
            .deadLetterRoutingKey(UserMqNames.DEAD_ROUTE)
            .build();
        var dead = QueueBuilder.durable(UserMqNames.DEAD_QUEUE).build();
        return new Declarables(
            exchange,
            codes,
            notifications,
            dead,
            new TopicExchange(UserApprovedEvent.EXCHANGE, true, false),
            BindingBuilder.bind(codes).to(exchange).with(UserMqNames.CODE_ROUTE),
            BindingBuilder.bind(notifications).to(exchange).with(UserMqNames.NOTIFICATION_ROUTE),
            BindingBuilder.bind(dead).to(exchange).with(UserMqNames.DEAD_ROUTE)
        );
    }
}

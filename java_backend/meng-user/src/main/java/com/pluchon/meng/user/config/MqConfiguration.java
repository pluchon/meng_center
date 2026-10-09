package com.pluchon.meng.user.config;

import com.pluchon.meng.api.user.event.UserApprovedEvent;
import com.pluchon.meng.user.mq.UserMqNames;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.*;

@Configuration
public class MqConfiguration {

    @Bean
    JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
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

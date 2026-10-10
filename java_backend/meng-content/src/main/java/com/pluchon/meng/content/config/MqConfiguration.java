package com.pluchon.meng.content.config;

import com.pluchon.meng.api.content.event.PostPublishedEvent;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.*;

@Configuration
public class MqConfiguration {

    @Bean
    TopicExchange contentExchange() {
        return new TopicExchange(PostPublishedEvent.EXCHANGE, true, false);
    }
}

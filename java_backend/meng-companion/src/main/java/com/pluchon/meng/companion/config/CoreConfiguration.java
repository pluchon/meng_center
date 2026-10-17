package com.pluchon.meng.companion.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 有界执行队列避免长对话耗尽请求线程
@Configuration
public class CoreConfiguration {
    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @Bean
    JacksonJsonMessageConverter messageConverter() { return new JacksonJsonMessageConverter(); }

    @Bean
    TopicExchange companionExchange() { return new TopicExchange("meng.companion.topic", true, false); }

    @Bean
    ThreadPoolTaskExecutor companionExecutor() {
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(12);
        executor.setQueueCapacity(12);
        executor.setThreadNamePrefix("companion-");
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}

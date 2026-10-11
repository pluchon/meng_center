package com.pluchon.meng.content.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// 搜索写入和维护任务的有界线程池配置
@Configuration
public class ReadModelConfiguration {

    @Bean("contentIndexExecutor")
    ThreadPoolTaskExecutor indexExecutor() {
        return executor("content-index-", 1, 256);
    }

    @Bean("contentMaintenanceExecutor")
    ThreadPoolTaskExecutor maintenanceExecutor() {
        return executor("content-maintenance-", 1, 4);
    }

    private ThreadPoolTaskExecutor executor(String prefix, int threads, int queue) {
        var e = new ThreadPoolTaskExecutor();
        e.setCorePoolSize(threads);
        e.setMaxPoolSize(threads);
        e.setQueueCapacity(queue);
        e.setThreadNamePrefix(prefix);
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(5);
        return e;
    }
}

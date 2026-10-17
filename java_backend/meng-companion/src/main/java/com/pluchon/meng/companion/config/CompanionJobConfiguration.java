package com.pluchon.meng.companion.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

// 执行器令牌只从环境读取；调度中心故障不影响普通聊天。
@Configuration
public class CompanionJobConfiguration {

    @Bean
    XxlJobSpringExecutor companionJobExecutor(Environment environment) {
        String token = System.getenv("MENG_XXL_JOB_ACCESS_TOKEN");
        if (
            token == null ||
            token.isBlank() ||
            "default_token".equals(token.strip())
        ) throw new IllegalStateException(
            "MENG_XXL_JOB_ACCESS_TOKEN must be configured"
        );
        var executor = new XxlJobSpringExecutor();
        executor.setAccessToken(token);
        executor.setAdminAddresses(
            environment.getRequiredProperty("meng.companion.job.admin-address")
        );
        executor.setAppname("meng-companion");
        executor.setIp("127.0.0.1");
        executor.setPort(
            environment.getRequiredProperty(
                "meng.companion.job.port",
                Integer.class
            )
        );
        executor.setAddress(
            environment.getRequiredProperty("meng.companion.job.address")
        );
        executor.setLogPath(
            environment.getRequiredProperty("meng.companion.job.log-path")
        );
        executor.setLogRetentionDays(7);
        executor.setGlueEnabled(false);
        return executor;
    }
}

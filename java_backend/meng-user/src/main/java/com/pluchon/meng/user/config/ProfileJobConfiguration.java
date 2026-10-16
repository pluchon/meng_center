package com.pluchon.meng.user.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// 资料审核发件箱的任务执行器配置
@Configuration
@ConditionalOnProperty(
    name = "meng.user.profile-moderation.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ProfileJobConfiguration {

    // 动态配置读取
    @Autowired
    private Environment environment;

    @Bean
    XxlJobSpringExecutor profileJobExecutor() {
        String token = System.getenv("MENG_XXL_JOB_ACCESS_TOKEN");
        if (
            token == null || token.isBlank() || "default_token".equals(token.trim())
        ) throw new IllegalStateException("MENG_XXL_JOB_ACCESS_TOKEN must be configured");
        var executor = new XxlJobSpringExecutor();
        executor.setAppname("meng-user");
        executor.setAccessToken(token);
        executor.setAdminAddresses(environment.getRequiredProperty("meng.user.job.admin-address"));
        executor.setIp("127.0.0.1");
        executor.setPort(environment.getRequiredProperty("meng.user.job.port", Integer.class));
        executor.setAddress(environment.getRequiredProperty("meng.user.job.address"));
        executor.setLogPath(environment.getRequiredProperty("meng.user.job.log-path"));
        executor.setLogRetentionDays(7);
        executor.setGlueEnabled(false);
        return executor;
    }
}

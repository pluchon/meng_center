package com.pluchon.meng.content.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

// 内容域任务执行器配置与令牌校验
@Configuration
public class ContentJobConfiguration {

    // 启动环境与配置属性
    @Autowired
    private Environment environment;

    public static String requireToken(String token) {
        if (token == null || token.isBlank() || "default_token".equals(token.trim())) {
            throw new IllegalStateException(
                    "MENG_XXL_JOB_ACCESS_TOKEN must be set in the environment, nonblank and"
                            + " different from default_token");
        }
        return token;
    }

    @Bean
    XxlJobSpringExecutor contentJobExecutor() {
        var executor = new XxlJobSpringExecutor();
        executor.setAccessToken(requireToken(System.getenv("MENG_XXL_JOB_ACCESS_TOKEN")));
        executor.setAdminAddresses(
                environment.getRequiredProperty("meng.content.job.admin-address"));
        executor.setAppname("meng-content");
        executor.setIp("127.0.0.1");
        executor.setPort(environment.getRequiredProperty("meng.content.job.port", Integer.class));
        executor.setAddress(environment.getRequiredProperty("meng.content.job.address"));
        executor.setLogPath(environment.getRequiredProperty("meng.content.job.log-path"));
        executor.setLogRetentionDays(7);
        executor.setGlueEnabled(false);
        return executor;
    }
}

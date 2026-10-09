package com.pluchon.meng.user.config;

import com.pluchon.meng.user.service.AdminInitializationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "meng.initialize-admin", havingValue = "true")
public class AdminInitializationConfiguration {

    @Bean
    ApplicationRunner initializeAdministrator(
        AdminInitializationService service,
        ConfigurableApplicationContext context
    ) {
        return arguments -> {
            service.initialize(System.getenv("MENG_ADMIN_USERNAME"), System.getenv("MENG_ADMIN_PASSWORD"));
            context.close();
        };
    }
}

package com.pluchon.meng.user.config;

import cloud.tianai.captcha.cache.CacheStore;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CaptchaConfiguration {

    @Bean
    ResourceStore resourceStore() {
        var store = new LocalMemoryResourceStore();
        store.addResource(
            cloud.tianai.captcha.common.constant.CaptchaTypeConstant.SLIDER,
            new cloud.tianai.captcha.resource.common.model.dto.Resource("classpath", "captcha/background.png")
        );
        return store;
    }
}

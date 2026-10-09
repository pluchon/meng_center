package com.pluchon.meng.user.config;

import cloud.tianai.captcha.common.constant.CaptchaTypeConstant;
import cloud.tianai.captcha.resource.DefaultBuiltInResources;
import cloud.tianai.captcha.resource.ResourceStore;
import cloud.tianai.captcha.resource.common.model.dto.Resource;
import cloud.tianai.captcha.resource.impl.LocalMemoryResourceStore;
import cloud.tianai.captcha.validator.ImageCaptchaValidator;
import cloud.tianai.captcha.validator.impl.BasicCaptchaTrackValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 行为验证码只启用滑块：自有背景图、内置滑块模板、位置与轨迹两层判定
@Configuration
public class CaptchaConfiguration {

    // 背景图数量，对应 resources/captcha 下的 bg-1.jpg 到 bg-5.jpg，尺寸均为 600×360
    private static final int BACKGROUND_COUNT = 5;

    @Bean
    ResourceStore resourceStore() {
        var store = new LocalMemoryResourceStore();
        for (int index = 1; index <= BACKGROUND_COUNT; index++) {
            store.addResource(CaptchaTypeConstant.SLIDER, new Resource("classpath", "captcha/bg-" + index + ".jpg"));
        }
        // 只注册滑块模板，其他验证码类型没有模板，无法生成
        new DefaultBuiltInResources(DefaultBuiltInResources.PATH_PREFIX).addDefaultTemplate(
            CaptchaTypeConstant.SLIDER,
            store
        );
        return store;
    }

    // 在位置判定之上叠加基础轨迹检测
    @Bean
    ImageCaptchaValidator imageCaptchaValidator() {
        return new BasicCaptchaTrackValidator();
    }
}

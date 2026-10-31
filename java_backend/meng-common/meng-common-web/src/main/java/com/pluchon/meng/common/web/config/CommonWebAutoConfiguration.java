package com.pluchon.meng.common.web.config;

import com.pluchon.meng.common.web.handler.GlobalExceptionHandler;
import com.pluchon.meng.common.web.interceptor.IdentityInterceptor;
import com.pluchon.meng.common.web.region.IpRegionService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

// Servlet 服务的公共装配：统一异常处理和身份拦截器
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({ GlobalExceptionHandler.class, IpRegionService.class })
public class CommonWebAutoConfiguration implements WebMvcConfigurer {

    // 数字和布尔值不再静默转成字符串字段，类型错误统一按参数错误返回 400
    @Bean
    public JsonMapperBuilderCustomizer strictTextFields() {
        return builder -> builder.withCoercionConfig(LogicalType.Textual, config -> {
            config.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
            config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        });
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdentityInterceptor());
    }
}

package com.pluchon.meng.common.web.config;

import com.pluchon.meng.common.web.handler.GlobalExceptionHandler;
import com.pluchon.meng.common.web.interceptor.IdentityInterceptor;
import com.pluchon.meng.common.web.region.IpRegionService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Servlet 服务的公共装配：统一异常处理和身份拦截器
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({ GlobalExceptionHandler.class, IpRegionService.class })
public class CommonWebAutoConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new IdentityInterceptor());
    }
}

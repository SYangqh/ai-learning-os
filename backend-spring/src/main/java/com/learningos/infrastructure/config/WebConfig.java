package com.learningos.infrastructure.config;

import com.learningos.infrastructure.ratelimit.UserRateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 全局配置：注册限流拦截器、启用 @Async（供 ObservabilityService 异步写入）。
 */
@Configuration
@EnableAsync
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private UserRateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 对所有 /api/session/** 和 /api/path/** 启用用户级限流
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/session/**", "/api/path/**");
    }
}

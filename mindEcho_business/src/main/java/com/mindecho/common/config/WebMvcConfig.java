package com.mindecho.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindecho.common.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置
 * ObjectMapper 通过 @Qualifier("webObjectMapper") 注入，不影响 Spring AI 使用的 Primary ObjectMapper
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final ObjectMapper objectMapper;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        @Qualifier("webObjectMapper") ObjectMapper objectMapper) {
        this.authInterceptor = authInterceptor;
        this.objectMapper = objectMapper;
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);
        converters.add(0, converter);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/auth/wx-login",
                        "/personality/list",
                        "/payment/wx-callback",
                        "/actuator/**"
                        // 注意：/astrology/** 不能加入白名单，所有星盘接口均需 JWT 认证
                        // (AstrologyController 内部使用了 UserContext.getUserId())
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}


package com.epint.ztb.aiks.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.epint.ztb.aiks.security.ApiKeyAuthFilter;

/**
 * Web 过滤器注册
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(AiksProperties props) {
        FilterRegistrationBean<ApiKeyAuthFilter> registration =
                new FilterRegistrationBean<>(new ApiKeyAuthFilter(props));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(10);
        return registration;
    }
}

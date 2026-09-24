package com.claimpilot.cache;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.claimpilot.config.AppProperties;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiter limiter;
    private final AppProperties properties;

    public WebConfig(RateLimiter limiter, AppProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(limiter, properties.cache().modelRequestsPerMinute()))
                .addPathPatterns("/api/**");
    }
}

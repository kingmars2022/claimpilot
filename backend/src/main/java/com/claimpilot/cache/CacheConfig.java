package com.claimpilot.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.claimpilot.config.AppProperties;

/** In memory by default; Redis when {@code claimpilot.cache.type=redis} (the "cache" profile). */
@Configuration
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    @Bean
    ModelCache modelCache(AppProperties properties, ObjectProvider<StringRedisTemplate> redis) {
        if (properties.cache().redis()) {
            log.info("Model replies are cached in Redis");
            return new RedisModelCache(redis.getObject());
        }
        return new MemoryModelCache();
    }

    @Bean
    RateLimiter rateLimiter(AppProperties properties, ObjectProvider<StringRedisTemplate> redis) {
        return properties.cache().redis() ? new RedisRateLimiter(redis.getObject()) : new MemoryRateLimiter();
    }
}

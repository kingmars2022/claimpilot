package com.claimpilot.cache;

import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;

/** One counter per key and window in Redis (INCR, then EXPIRE on the first request). */
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Duration acquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long size = window.toMillis();
        long index = now / size;
        String redisKey = "claimpilot:rate:" + key + ":" + index;
        Long count = redis.opsForValue().increment(redisKey);
        if (count != null && count == 1) {
            redis.expire(redisKey, window.plusSeconds(1));
        }
        return count != null && count <= limit ? Duration.ZERO : Duration.ofMillis((index + 1) * size - now);
    }
}

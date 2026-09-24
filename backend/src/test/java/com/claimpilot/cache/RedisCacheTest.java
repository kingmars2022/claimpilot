package com.claimpilot.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

/** The Redis versions against a real Redis in Docker. */
class RedisCacheTest {

    @SuppressWarnings("resource")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connections;
    static StringRedisTemplate template;

    @BeforeAll
    static void start() {
        redis.start();
        connections = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connections.afterPropertiesSet();
        connections.start();
        template = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stop() {
        connections.destroy();
        redis.stop();
    }

    @Test
    void repliesAreSharedExpireInRedisAndAreEvictedPerUser() {
        RedisModelCache cache = new RedisModelCache(template);
        String key = ModelCache.keyOf("system", "question");

        cache.put(7L, key, "cached reply", Duration.ofMinutes(10));
        cache.put(8L, key, "another user", Duration.ofMinutes(10));

        assertThat(new RedisModelCache(template).get(7L, key)).as("seen by another server").contains("cached reply");
        assertThat(template.getExpire("claimpilot:model:7:" + key)).isPositive();
        cache.evictOwner(7L);
        assertThat(cache.get(7L, key)).isEmpty();
        assertThat(cache.get(8L, key)).contains("another user");
    }

    @Test
    void theLimitIsSharedAcrossServers() {
        RedisRateLimiter serverA = new RedisRateLimiter(template);
        RedisRateLimiter serverB = new RedisRateLimiter(template);

        assertThat(serverA.acquire("user:test", 2, Duration.ofMinutes(1))).isZero();
        assertThat(serverB.acquire("user:test", 2, Duration.ofMinutes(1))).isZero();
        assertThat(serverA.acquire("user:test", 2, Duration.ofMinutes(1))).isPositive();
    }
}

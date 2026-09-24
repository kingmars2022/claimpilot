package com.claimpilot.cache;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Shared by every server instance; Redis expires the entries itself. */
public class RedisModelCache implements ModelCache {

    private static final String PREFIX = "claimpilot:model:";

    private final StringRedisTemplate redis;

    public RedisModelCache(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Optional<String> get(Long ownerId, String promptKey) {
        return Optional.ofNullable(redis.opsForValue().get(key(ownerId, promptKey)));
    }

    @Override
    public void put(Long ownerId, String promptKey, String reply, Duration ttl) {
        redis.opsForValue().set(key(ownerId, promptKey), reply, ttl);
    }

    @Override
    public void evictOwner(Long ownerId) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(PREFIX + ownerId + ":*").count(500).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static String key(Long ownerId, String promptKey) {
        return PREFIX + ownerId + ":" + promptKey;
    }
}

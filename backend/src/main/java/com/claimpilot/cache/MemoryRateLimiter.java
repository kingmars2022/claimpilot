package com.claimpilot.cache;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Counts per server; with several servers, use the Redis limiter so the limit is shared. */
public class MemoryRateLimiter implements RateLimiter {

    private record Window(long index, int count) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Duration acquire(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        long size = window.toMillis();
        long index = now / size;
        Window current = windows.compute(key, (k, w) -> w == null || w.index() != index
                ? new Window(index, 1) : new Window(index, w.count() + 1));
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> w.index() < index);
        }
        return current.count() <= limit ? Duration.ZERO : Duration.ofMillis((index + 1) * size - now);
    }
}

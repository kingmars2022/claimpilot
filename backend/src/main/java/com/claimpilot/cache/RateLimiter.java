package com.claimpilot.cache;

import java.time.Duration;

/** Fixed-window request counting per key, for example per user per minute. */
public interface RateLimiter {

    /**
     * Counts one request.
     *
     * @return how long to wait before retrying, or {@link Duration#ZERO} when the request is allowed
     */
    Duration acquire(String key, int limit, Duration window);
}

package com.claimpilot.security;

import java.time.Duration;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.claimpilot.cache.RateLimiter;
import com.claimpilot.common.TooManyRequestsException;
import com.claimpilot.config.AppProperties;

/**
 * Slows down password guessing and mass sign-ups: attempts are counted per client address and,
 * for sign-in, per username (so spreading guesses over many addresses does not help either).
 * Shared across servers when the limiter is in Redis.
 */
@Component
public class LoginThrottle {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiter limiter;
    private final int perMinute;

    @Autowired
    public LoginThrottle(RateLimiter limiter, AppProperties properties) {
        this(limiter, properties.cache().loginAttemptsPerMinute());
    }

    LoginThrottle(RateLimiter limiter, int perMinute) {
        this.limiter = limiter;
        this.perMinute = perMinute;
    }

    public void signIn(String username, String clientAddress) {
        check("login:ip:" + clientAddress);
        check("login:user:" + username.strip().toLowerCase(Locale.ROOT));
    }

    public void signUp(String clientAddress) {
        check("register:ip:" + clientAddress);
    }

    private void check(String key) {
        Duration wait = limiter.acquire(key, perMinute, WINDOW);
        if (!wait.isZero()) {
            throw new TooManyRequestsException("Too many attempts. Wait a minute and try again.", wait);
        }
    }
}

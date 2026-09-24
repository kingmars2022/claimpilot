package com.claimpilot.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class MemoryCacheTest {

    @Test
    void repliesArePerUserExpireAndCanBeEvicted() throws Exception {
        MemoryModelCache cache = new MemoryModelCache();
        String key = ModelCache.keyOf("system", "Is physio covered?");

        cache.put(1L, key, "Yes [1]", Duration.ofMinutes(5));
        cache.put(2L, key, "Other user's reply", Duration.ofMillis(1));

        assertThat(cache.get(1L, key)).contains("Yes [1]");
        Thread.sleep(5);
        assertThat(cache.get(2L, key)).as("expired").isEmpty();
        cache.evictOwner(1L);
        assertThat(cache.get(1L, key)).isEmpty();
    }

    @Test
    void theKeyDependsOnEveryPartOfThePrompt() {
        assertThat(ModelCache.keyOf("a", "bc")).isNotEqualTo(ModelCache.keyOf("ab", "c"));
        assertThat(ModelCache.keyOf("a", "b")).isEqualTo(ModelCache.keyOf("a", "b"));
    }

    @Test
    void requestsOverTheLimitWaitForTheNextWindow() {
        MemoryRateLimiter limiter = new MemoryRateLimiter();
        Duration minute = Duration.ofMinutes(1);

        assertThat(limiter.acquire("user:fiona", 2, minute)).isZero();
        assertThat(limiter.acquire("user:fiona", 2, minute)).isZero();
        assertThat(limiter.acquire("user:fiona", 2, minute)).isPositive().isLessThanOrEqualTo(minute);
        assertThat(limiter.acquire("user:sam", 2, minute)).as("counted per user").isZero();
    }

    @Test
    void overTheLimitTheRequestIsRejectedWith429AndRetryAfter() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "fiona", null, List.of()));
        try {
            RateLimitInterceptor interceptor = new RateLimitInterceptor(new MemoryRateLimiter(), 1);
            MockHttpServletResponse first = new MockHttpServletResponse();
            MockHttpServletResponse second = new MockHttpServletResponse();

            assertThat(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/chat"), first, null)).isTrue();
            assertThat(interceptor.preHandle(new MockHttpServletRequest("POST", "/api/chat"), second, null)).isFalse();

            assertThat(second.getStatus()).isEqualTo(429);
            assertThat(second.getHeader("Retry-After")).isNotBlank();
            assertThat(second.getContentAsString()).contains("Too many requests");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void onlyRequestsThatUseTheModelAreCounted() {
        assertThat(RateLimitInterceptor.counted(new MockHttpServletRequest("POST", "/api/chat"))).isTrue();
        assertThat(RateLimitInterceptor.counted(new MockHttpServletRequest("GET", "/api/claims/guide"))).isTrue();
        assertThat(RateLimitInterceptor.counted(new MockHttpServletRequest("GET", "/api/policies"))).isFalse();
        assertThat(RateLimitInterceptor.counted(new MockHttpServletRequest("PATCH", "/api/claims/x/fields/y")))
                .isFalse();
    }
}

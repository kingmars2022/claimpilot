package com.claimpilot.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.claimpilot.cache.MemoryRateLimiter;
import com.claimpilot.common.TooManyRequestsException;

class LoginThrottleTest {

    @Test
    void guessesAreLimitedPerAddress() {
        LoginThrottle throttle = new LoginThrottle(new MemoryRateLimiter(), 3);

        for (int i = 0; i < 3; i++) {
            throttle.signIn("user" + i, "10.0.0.1");
        }

        assertThatThrownBy(() -> throttle.signIn("user9", "10.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class)
                .satisfies(ex -> assertThat(((TooManyRequestsException) ex).retryAfterSeconds()).isPositive());
        assertThatNoException().isThrownBy(() -> throttle.signIn("user9", "10.0.0.2"));
    }

    @Test
    void guessesAreLimitedPerUsernameAcrossAddresses() {
        LoginThrottle throttle = new LoginThrottle(new MemoryRateLimiter(), 3);

        for (int i = 0; i < 3; i++) {
            throttle.signIn("Fiona", "10.0.1." + i);
        }

        assertThatThrownBy(() -> throttle.signIn(" fiona ", "10.0.1.99")).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void signUpsAreLimitedPerAddress() {
        LoginThrottle throttle = new LoginThrottle(new MemoryRateLimiter(), 2);

        throttle.signUp("10.0.2.1");
        throttle.signUp("10.0.2.1");

        assertThatThrownBy(() -> throttle.signUp("10.0.2.1")).isInstanceOf(TooManyRequestsException.class);
    }
}

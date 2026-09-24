package com.claimpilot.security;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.claimpilot.config.AppProperties;

class SecretsGuardTest {

    private static final String REAL_KEY = "c2VjcmV0LWtleS1mb3ItdGVzdHMtMDEyMzQ1Njc4OWFi";

    @Test
    void developmentKeysAreRefusedWhereTheyAreNotAllowed() {
        assertThatIllegalStateException()
                .isThrownBy(() -> SecretsGuard.check(properties("dev-only-secret-change-me-0123456789abcdef",
                        REAL_KEY, false)))
                .withMessageContaining("JWT secret");
        assertThatIllegalStateException()
                .isThrownBy(() -> SecretsGuard.check(properties("a-real-secret-of-at-least-32-characters",
                        SecretsGuard.DEV_FILE_KEY, false)))
                .withMessageContaining("file encryption key");
    }

    @Test
    void developmentKeysAreAcceptedLocallyAndRealKeysEverywhere() {
        assertThatNoException().isThrownBy(() -> SecretsGuard.check(properties(
                "dev-only-secret-change-me-0123456789abcdef", SecretsGuard.DEV_FILE_KEY, true)));
        assertThatNoException().isThrownBy(() -> SecretsGuard.check(properties(
                "a-real-secret-of-at-least-32-characters", REAL_KEY, false)));
    }

    private static AppProperties properties(String jwtSecret, String fileKey, boolean allowDev) {
        return new AppProperties(new AppProperties.Storage("local", "./data", fileKey, null), null, null,
                new AppProperties.Security(jwtSecret, Duration.ofHours(8), allowDev), null, null, null, null);
    }
}

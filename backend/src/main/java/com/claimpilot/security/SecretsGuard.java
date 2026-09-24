package com.claimpilot.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.claimpilot.config.AppProperties;

/**
 * The development JWT secret and file key are public (they are in this repository). They are
 * accepted on a laptop, with a warning; anywhere {@code claimpilot.security.allow-dev-secrets} is
 * false (the "aws" profile), the application refuses to start with them.
 */
@Component
public class SecretsGuard implements InitializingBean {

    static final String DEV_JWT_PREFIX = "dev-only-";
    static final String DEV_FILE_KEY = "ZGV2LW9ubHktZmlsZS1rZXktY2hhbmdlLW1lLTAxMjM=";

    private static final Logger log = LoggerFactory.getLogger(SecretsGuard.class);

    private final AppProperties properties;

    public SecretsGuard(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        check(properties);
    }

    static void check(AppProperties properties) {
        boolean devJwt = properties.security().jwtSecret() != null
                && properties.security().jwtSecret().startsWith(DEV_JWT_PREFIX);
        boolean devFileKey = DEV_FILE_KEY.equals(properties.storage().encryptionKey());
        if (!devJwt && !devFileKey) {
            return;
        }
        String which = devJwt && devFileKey ? "JWT secret and file encryption key are"
                : devJwt ? "JWT secret is" : "file encryption key is";
        if (!properties.security().allowDevSecrets()) {
            throw new IllegalStateException("The development " + which + " still in use. Set "
                    + "CLAIMPILOT_SECURITY_JWT_SECRET and CLAIMPILOT_STORAGE_ENCRYPTION_KEY before running here.");
        }
        log.warn("The development {} in use: fine on a laptop, never on a server", which);
    }
}

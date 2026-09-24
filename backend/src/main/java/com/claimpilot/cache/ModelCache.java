package com.claimpilot.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Model replies by prompt. The same prompt (same clauses, same question, same history) gets the
 * same reply without a second model call. Entries belong to one user, expire, and are removed when
 * the user deletes a policy or their account.
 */
public interface ModelCache {

    Optional<String> get(Long ownerId, String promptKey);

    void put(Long ownerId, String promptKey, String reply, Duration ttl);

    /** Removes every entry of this user. */
    void evictOwner(Long ownerId);

    /** A short key for a prompt: SHA-256 of all its parts. */
    static String keyOf(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update((part == null ? "" : part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}

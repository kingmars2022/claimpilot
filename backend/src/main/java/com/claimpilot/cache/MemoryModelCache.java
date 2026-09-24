package com.claimpilot.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Single-server cache; the default when Redis is not configured. */
public class MemoryModelCache implements ModelCache {

    private static final int MAX_ENTRIES = 2_000;

    private record Entry(String reply, Instant expiresAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(Long ownerId, String promptKey) {
        String key = ownerId + ":" + promptKey;
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAt().isBefore(Instant.now())) {
            entries.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.reply());
    }

    @Override
    public void put(Long ownerId, String promptKey, String reply, Duration ttl) {
        if (entries.size() >= MAX_ENTRIES) {
            Instant now = Instant.now();
            entries.values().removeIf(e -> e.expiresAt().isBefore(now));
            if (entries.size() >= MAX_ENTRIES) {
                entries.clear();  // simple bound; a real deployment uses Redis with its own eviction
            }
        }
        entries.put(ownerId + ":" + promptKey, new Entry(reply, Instant.now().plus(ttl)));
    }

    /** Empties the cache (used between tests). */
    public void clear() {
        entries.clear();
    }

    @Override
    public void evictOwner(Long ownerId) {
        String prefix = ownerId + ":";
        entries.keySet().removeIf(k -> k.startsWith(prefix));
    }
}

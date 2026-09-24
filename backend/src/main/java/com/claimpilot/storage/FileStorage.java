package com.claimpilot.storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Where uploaded files live. Phase 1 uses the local disk; phase 3 swaps in Amazon S3
 * without touching the callers.
 */
public interface FileStorage {

    /** Stores the stream and returns an opaque key used to load or delete it later. */
    String store(String originalFileName, InputStream content) throws IOException;

    Resource load(String key);

    void delete(String key) throws IOException;
}

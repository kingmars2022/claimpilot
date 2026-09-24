package com.claimpilot.storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * Where uploaded files live: the local disk or an S3 bucket, optionally encrypted at rest. The
 * implementation is chosen in {@link StorageConfig} without touching the callers.
 */
public interface FileStorage {

    /** Stores the stream and returns an opaque key used to load or delete it later. */
    String store(String originalFileName, InputStream content) throws IOException;

    Resource load(String key);

    void delete(String key) throws IOException;
}

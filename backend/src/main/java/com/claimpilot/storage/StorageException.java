package com.claimpilot.storage;

/** A stored file could not be read or written. */
public class StorageException extends RuntimeException {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

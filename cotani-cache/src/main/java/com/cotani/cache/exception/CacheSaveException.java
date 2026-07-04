package com.cotani.cache.exception;

import java.io.Serial;

/**
 * Thrown when a cache entry fails to persist to the repository.
 */
public final class CacheSaveException extends CacheException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CacheSaveException(String message, Throwable cause) {
        super(message, cause);
    }
}

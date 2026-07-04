package com.cotani.cache.exception;

import java.io.Serial;

/**
 * Thrown when a cache entry fails to load from the repository.
 */
public final class CacheLoadException extends CacheException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CacheLoadException(String message) {
        super(message);
    }

    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

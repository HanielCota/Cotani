package com.cotani.cache.exception;

import java.io.Serial;

/**
 * Base exception for cache errors.
 */
public class CacheException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

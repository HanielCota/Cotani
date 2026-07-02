package com.cotani.cache.exception;

public class CacheException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CacheException(String message) {
        super(message);
    }

    public CacheException(String message, Throwable cause) {
        super(message, cause);
    }
}

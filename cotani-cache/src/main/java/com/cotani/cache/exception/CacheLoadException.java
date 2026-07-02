package com.cotani.cache.exception;

public final class CacheLoadException extends CacheException {
    private static final long serialVersionUID = 1L;

    public CacheLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

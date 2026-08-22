package com.cotani.redis.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when connecting to the Redis cluster or server fails or drops unexpectedly.
 */
public final class RedisConnectionException extends RedisException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RedisConnectionException(String message) {
        super(message);
    }

    public RedisConnectionException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}

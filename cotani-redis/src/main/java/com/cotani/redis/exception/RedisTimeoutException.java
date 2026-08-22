package com.cotani.redis.exception;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when a Redis command or transaction exceeds its allocated deadline.
 */
public final class RedisTimeoutException extends RedisException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RedisTimeoutException(String message) {
        super(message);
    }

    public RedisTimeoutException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}

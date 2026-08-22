package com.cotani.redis.exception;

import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Base runtime exception for all Redis-related errors in Cotani.
 */
public class RedisException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RedisException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }

    public RedisException(String message, @Nullable Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
    }
}

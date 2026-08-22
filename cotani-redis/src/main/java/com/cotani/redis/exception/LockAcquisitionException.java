package com.cotani.redis.exception;

import com.cotani.redis.lock.LockKey;
import java.io.Serial;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Thrown when acquiring a distributed lock fails due to timeout, conflict or connection issues.
 */
public final class LockAcquisitionException extends RedisException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient LockKey key;

    public LockAcquisitionException(LockKey key, String message) {
        super(message);
        this.key = Objects.requireNonNull(key, "key");
    }

    public LockAcquisitionException(LockKey key, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.key = Objects.requireNonNull(key, "key");
    }

    public LockKey key() {
        return key;
    }
}

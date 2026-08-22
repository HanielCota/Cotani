package com.cotani.redis.lock;

import java.util.Objects;
import java.util.UUID;

/**
 * Value object representing an ownership lease token for a distributed lock.
 *
 * @param value unique lease token string
 */
public record LockToken(String value) {

    public LockToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Lock token must not be blank");
        }
    }

    public static LockToken random() {
        return new LockToken(UUID.randomUUID().toString());
    }

    public static LockToken of(String value) {
        return new LockToken(value);
    }
}

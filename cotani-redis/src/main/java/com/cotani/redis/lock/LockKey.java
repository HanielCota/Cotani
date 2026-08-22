package com.cotani.redis.lock;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Value object identifying a distributed mutual exclusion lock.
 *
 * @param value lock identifier string
 */
public record LockKey(String value) {

    private static final Pattern VALID_KEY = Pattern.compile("^[a-zA-Z0-9_.:\\-]+$");

    public LockKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Lock key must not be blank");
        }
        if (!VALID_KEY.matcher(value).matches()) {
            throw new IllegalArgumentException("Lock key contains invalid characters: " + value);
        }
    }

    public static LockKey of(String value) {
        return new LockKey(value);
    }
}

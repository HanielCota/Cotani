package com.cotani.redis.store;

import java.util.Objects;

/**
 * Value object representing a key in Redis storage.
 *
 * @param value key string
 */
public record RedisKey(String value) {

    public RedisKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Redis key must not be blank");
        }
    }

    public static RedisKey of(String value) {
        return new RedisKey(value);
    }
}

package com.cotani.cache.invalidation;

import java.util.Objects;
import java.util.UUID;

/** Identifies a cache key changed by one cache instance. */
public record CacheInvalidation<K>(UUID sourceId, K key) {
    public CacheInvalidation {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(key, "key");
    }
}

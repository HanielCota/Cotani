package com.cotani.cache.invalidation;

/** A closeable invalidation-bus subscription. */
@FunctionalInterface
public interface CacheInvalidationSubscription extends AutoCloseable {

    @Override
    void close();

    static CacheInvalidationSubscription noop() {
        return () -> {};
    }
}

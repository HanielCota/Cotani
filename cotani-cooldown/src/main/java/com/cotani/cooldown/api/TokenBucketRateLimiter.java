package com.cotani.cooldown.api;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TokenBucketRateLimiter<K> {
    TokenBucket bucket(K key);

    default boolean tryAcquire(K key) {
        return tryAcquire(key, 1L);
    }

    default boolean tryAcquire(K key, long tokens) {
        return bucket(key).tryConsume(tokens);
    }

    void remove(K key);

    void clear();

    static <K> TokenBucketRateLimiter<K> create(long capacity, double refillRatePerSecond) {
        return new com.cotani.cooldown.internal.DefaultTokenBucketRateLimiter<>(capacity, refillRatePerSecond);
    }
}

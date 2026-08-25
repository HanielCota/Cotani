package com.cotani.cooldown.api;

import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TokenBucket {
    long capacity();

    double refillRatePerSecond();

    long availableTokens();

    default boolean tryConsume() {
        return tryConsume(1L);
    }

    boolean tryConsume(long tokens);

    static TokenBucket of(long capacity, double refillRatePerSecond) {
        return new com.cotani.cooldown.internal.AtomicTokenBucket(capacity, refillRatePerSecond);
    }
}

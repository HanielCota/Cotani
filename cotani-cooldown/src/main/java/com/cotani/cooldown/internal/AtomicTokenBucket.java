package com.cotani.cooldown.internal;

import com.cotani.api.InternalApi;
import com.cotani.cooldown.api.TokenBucket;
import org.jspecify.annotations.NullMarked;

@InternalApi
@NullMarked
public final class AtomicTokenBucket implements TokenBucket {
    private final long capacity;
    private final double refillRatePerSecond;
    private final Object lock = new Object();
    private double tokens;
    private long lastRefillNanos;

    public AtomicTokenBucket(long capacity, double refillRatePerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive: " + refillRatePerSecond);
        }
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = (double) capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    @Override
    public long capacity() {
        return capacity;
    }

    @Override
    public double refillRatePerSecond() {
        return refillRatePerSecond;
    }

    @Override
    public long availableTokens() {
        synchronized (lock) {
            refill();
            return (long) Math.floor(tokens);
        }
    }

    @Override
    public boolean tryConsume(long tokensToConsume) {
        if (tokensToConsume <= 0) {
            throw new IllegalArgumentException("Tokens to consume must be positive: " + tokensToConsume);
        }
        if (tokensToConsume > capacity) {
            return false;
        }

        synchronized (lock) {
            refill();
            if (tokens >= tokensToConsume) {
                tokens -= tokensToConsume;
                return true;
            }
            return false;
        }
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos > 0) {
            double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
            tokens = Math.min((double) capacity, tokens + (elapsedSeconds * refillRatePerSecond));
            lastRefillNanos = now;
        }
    }
}

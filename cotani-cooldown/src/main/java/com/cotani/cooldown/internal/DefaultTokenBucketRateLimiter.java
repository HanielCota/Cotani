package com.cotani.cooldown.internal;

import com.cotani.api.InternalApi;
import com.cotani.cooldown.api.TokenBucket;
import com.cotani.cooldown.api.TokenBucketRateLimiter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;

@InternalApi
@NullMarked
public final class DefaultTokenBucketRateLimiter<K> implements TokenBucketRateLimiter<K> {
    private final long capacity;
    private final double refillRatePerSecond;
    private final Map<K, TokenBucket> buckets = new ConcurrentHashMap<>();

    private final java.util.concurrent.atomic.AtomicLong lastCleanupNanos =
            new java.util.concurrent.atomic.AtomicLong();
    private static final long CLEANUP_INTERVAL_NANOS =
            java.time.Duration.ofMinutes(1).toNanos();

    public DefaultTokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive: " + capacity);
        }
        if (refillRatePerSecond <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive: " + refillRatePerSecond);
        }
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
    public TokenBucket bucket(K key) {
        Objects.requireNonNull(key, "key");
        cleanupIdleBucketsIfNeeded();
        return buckets.computeIfAbsent(key, _ -> new AtomicTokenBucket(capacity, refillRatePerSecond));
    }

    private void cleanupIdleBucketsIfNeeded() {
        var now = System.nanoTime();
        var last = lastCleanupNanos.get();
        if ((now - last > CLEANUP_INTERVAL_NANOS || buckets.size() > 10_000)
                && lastCleanupNanos.compareAndSet(last, now)) {
            buckets.entrySet().removeIf(entry -> entry.getValue().availableTokens() >= capacity);
        }
    }

    @Override
    public void remove(K key) {
        Objects.requireNonNull(key, "key");
        buckets.remove(key);
    }

    @Override
    public void clear() {
        buckets.clear();
    }
}

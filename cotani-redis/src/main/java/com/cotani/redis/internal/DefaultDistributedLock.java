package com.cotani.redis.internal;

import com.cotani.api.InternalApi;
import com.cotani.redis.lock.DistributedLock;
import com.cotani.redis.lock.LockKey;
import com.cotani.redis.lock.LockToken;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Default implementation of {@link DistributedLock} with monotonic nanoTime expiry checking.
 */
@InternalApi
public final class DefaultDistributedLock implements DistributedLock {

    private static final Logger LOGGER = Logger.getLogger(DefaultDistributedLock.class.getName());

    private final LockKey key;
    private final LockToken token;
    private final Duration leaseTime;
    private final long deadlineNanos;
    private final BiFunction<LockKey, LockToken, CompletionStage<Void>> releaseFunction;
    private final AtomicBoolean held = new AtomicBoolean(true);

    public DefaultDistributedLock(
            LockKey key,
            LockToken token,
            Duration leaseTime,
            BiFunction<LockKey, LockToken, CompletionStage<Void>> releaseFunction) {
        this.key = Objects.requireNonNull(key, "key");
        this.token = Objects.requireNonNull(token, "token");
        this.leaseTime = Objects.requireNonNull(leaseTime, "leaseTime");
        this.deadlineNanos = System.nanoTime() + leaseTime.toNanos();
        this.releaseFunction = Objects.requireNonNull(releaseFunction, "releaseFunction");
    }

    @Override
    public LockKey key() {
        return key;
    }

    @Override
    public LockToken token() {
        return token;
    }

    @Override
    public Duration leaseTime() {
        return leaseTime;
    }

    @Override
    public boolean isHeld() {
        return held.get() && System.nanoTime() < deadlineNanos;
    }

    @Override
    public CompletionStage<Void> releaseAsync() {
        if (!held.compareAndSet(true, false)) {
            return CompletableFuture.completedFuture(null);
        }
        return releaseFunction.apply(key, token);
    }

    @Override
    public void close() {
        releaseAsync().whenComplete((_, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to release distributed lock: " + key, failure);
            }
        });
    }
}

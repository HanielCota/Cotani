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
import org.bukkit.Bukkit;

/**
 * Default implementation of {@link DistributedLock} with monotonic nanoTime expiry checking.
 */
@InternalApi
public final class DefaultDistributedLock implements DistributedLock {

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
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "DistributedLock.close() must not be called from the server main thread. Use releaseAsync() instead.");
        }
        try {
            releaseAsync().toCompletableFuture().get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while releasing distributed lock", e);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException("Failed to release distributed lock", cause);
        }
    }
}

package com.cotani.redis.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.redis.lock.LockKey;
import com.cotani.redis.lock.LockToken;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DefaultDistributedLockTest {

    @Test
    void shouldReleaseOnlyOnce() {
        var key = LockKey.of("test:key");
        var token = LockToken.of("test:token");
        var releaseCount = new AtomicInteger();

        var lock = new DefaultDistributedLock(key, token, Duration.ofSeconds(5), (k, t) -> {
            releaseCount.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });

        assertTrue(lock.isHeld());
        assertEquals(key, lock.key());
        assertEquals(token, lock.token());
        assertEquals(Duration.ofSeconds(5), lock.leaseTime());

        lock.releaseAsync().toCompletableFuture().join();

        assertFalse(lock.isHeld());
        assertEquals(1, releaseCount.get());

        // Second release should be a no-op
        lock.releaseAsync().toCompletableFuture().join();
        assertEquals(1, releaseCount.get());
    }

    @Test
    void shouldReportNotHeldWhenExpired() {
        var key = LockKey.of("test:expired");
        var token = LockToken.of("test:token");

        // 1 nanosecond lease
        var lock = new DefaultDistributedLock(
                key, token, Duration.ofNanos(1), (k, t) -> CompletableFuture.completedFuture(null));

        // Spin until monotonic time advances beyond 1 nano
        long startNanos = System.nanoTime();
        while (System.nanoTime() <= startNanos + 1000) {
            // spin-wait
        }

        assertFalse(lock.isHeld());
    }
}

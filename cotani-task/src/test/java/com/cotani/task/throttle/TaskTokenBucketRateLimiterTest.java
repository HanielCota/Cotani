package com.cotani.task.throttle;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

class TaskTokenBucketRateLimiterTest {
    @Test
    void firstAcquireSucceeds() {
        RateLimiter limiter = TaskTokenBucketRateLimiter.create(2, Duration.ofSeconds(1));

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> TaskTokenBucketRateLimiter.create(0, Duration.ofSeconds(1)));
    }

    @Test
    void rejectsZeroRefillPeriod() {
        assertThrows(IllegalArgumentException.class, () -> TaskTokenBucketRateLimiter.create(1, Duration.ZERO));
    }

    @Test
    void respectsRefillRate() {
        RateLimiter limiter = TaskTokenBucketRateLimiter.create(1, Duration.ofMillis(100));

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        LockSupport.parkNanos(Duration.ofMillis(110).toNanos());

        assertTrue(limiter.tryAcquire());
    }
}

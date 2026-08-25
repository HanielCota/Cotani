package com.cotani.task.throttle;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
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
    @SuppressWarnings("java:S2925")
    void respectsRefillRate() throws InterruptedException {
        RateLimiter limiter = TaskTokenBucketRateLimiter.create(1, Duration.ofMillis(100));

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(110);

        assertTrue(limiter.tryAcquire());
    }
}

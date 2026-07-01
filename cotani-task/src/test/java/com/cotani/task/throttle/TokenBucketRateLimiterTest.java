package com.cotani.task.throttle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

    @Test
    void firstAcquireSucceeds() {
        RateLimiter limiter = new TokenBucketRateLimiter(2, Duration.ofSeconds(1));

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void tryAcquireWithTimeoutWaitsForRefill() throws InterruptedException {
        RateLimiter limiter = new TokenBucketRateLimiter(1, Duration.ofMillis(50));

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        assertTrue(limiter.tryAcquire(Duration.ofMillis(200)));
    }

    @Test
    void rejectsZeroCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(0, Duration.ofSeconds(1)));
    }

    @Test
    void rejectsZeroRefillPeriod() {
        assertThrows(IllegalArgumentException.class, () -> new TokenBucketRateLimiter(1, Duration.ZERO));
    }

    @Test
    void respectsRefillRate() throws InterruptedException {
        RateLimiter limiter = new TokenBucketRateLimiter(1, Duration.ofMillis(100));

        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        Thread.sleep(110);

        assertTrue(limiter.tryAcquire());
    }
}

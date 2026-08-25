package com.cotani.cooldown.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TokenBucketTest {
    @Test
    void shouldConsumeTokensWithinCapacity() {
        TokenBucket bucket = TokenBucket.of(10, 2.0);
        assertEquals(10, bucket.capacity());
        assertEquals(10, bucket.availableTokens());

        assertTrue(bucket.tryConsume(5));
        assertEquals(5, bucket.availableTokens());
        assertTrue(bucket.tryConsume(5));
        assertEquals(0, bucket.availableTokens());
        assertFalse(bucket.tryConsume(1));
    }

    @Test
    void shouldRefillOverTime() throws InterruptedException {
        TokenBucket bucket = TokenBucket.of(5, 10.0); // 10 tokens per second
        assertTrue(bucket.tryConsume(5));
        assertEquals(0, bucket.availableTokens());

        Thread.sleep(250); // ~2.5 tokens
        assertTrue(bucket.availableTokens() >= 2);
        assertTrue(bucket.tryConsume(2));
    }

    @Test
    void shouldRejectInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> TokenBucket.of(0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> TokenBucket.of(5, 0));
        TokenBucket bucket = TokenBucket.of(5, 1.0);
        assertThrows(IllegalArgumentException.class, () -> bucket.tryConsume(0));
        assertThrows(IllegalArgumentException.class, () -> bucket.tryConsume(-1));
    }

    @Test
    void shouldManagePerKeyRateLimiter() {
        TokenBucketRateLimiter<String> limiter = TokenBucketRateLimiter.create(3, 1.0);
        assertTrue(limiter.tryAcquire("user1", 2));
        assertTrue(limiter.tryAcquire("user2", 3));
        assertFalse(limiter.tryAcquire("user2", 1));
        assertTrue(limiter.tryAcquire("user1", 1));
        assertFalse(limiter.tryAcquire("user1", 1));

        limiter.remove("user1");
        // Fresh bucket for user1
        assertTrue(limiter.tryAcquire("user1", 3));
    }
}

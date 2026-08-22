package com.cotani.redis.ratelimit;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Distributed rate limiter backed by Redis atomic sliding window Lua scripts.
 */
public interface RedisRateLimiter {

    /**
     * Attempts to acquire a single permit for a rate limit key.
     *
     * @param rateLimitKey target key (e.g. "player:command:pay:uuid")
     * @param maxRequests max requests allowed in the time window
     * @param window duration of the sliding window
     * @return stage completing with the rate limit decision
     */
    CompletionStage<RateLimitResult> tryAcquireAsync(String rateLimitKey, int maxRequests, Duration window);

    /**
     * Attempts to acquire multiple permits for a rate limit key.
     *
     * @param rateLimitKey target key
     * @param tokens number of tokens/permits to acquire
     * @param maxRequests max requests allowed in the time window
     * @param window duration of the sliding window
     * @return stage completing with the rate limit decision
     */
    CompletionStage<RateLimitResult> tryAcquireAsync(String rateLimitKey, int tokens, int maxRequests, Duration window);

    /**
     * Resets/clears the rate limit history for a key immediately.
     *
     * @param rateLimitKey target key
     * @return stage completing once reset
     */
    CompletionStage<Void> resetAsync(String rateLimitKey);
}

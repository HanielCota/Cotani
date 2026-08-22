package com.cotani.redis.ratelimit;

import java.time.Duration;
import java.util.Objects;

/**
 * Result of a rate limit check against a sliding window.
 *
 * @param allowed true if request is within allowed limits and token was acquired
 * @param currentRequests number of requests currently registered in the window
 * @param maxRequests maximum capacity permitted in the window
 * @param resetAfter duration until the oldest request slot clears
 */
public record RateLimitResult(boolean allowed, long currentRequests, long maxRequests, Duration resetAfter) {

    public RateLimitResult {
        Objects.requireNonNull(resetAfter, "resetAfter");
    }
}

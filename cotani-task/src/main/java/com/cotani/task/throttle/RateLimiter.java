package com.cotani.task.throttle;

import java.time.Duration;

public interface RateLimiter {

    boolean tryAcquire();

    /**
     * Returns the suggested non-negative delay to wait before the next {@link #tryAcquire()} attempt.
     * A zero duration means the caller may retry immediately.
     */
    default Duration retryDelay() {
        return Duration.ZERO;
    }
}

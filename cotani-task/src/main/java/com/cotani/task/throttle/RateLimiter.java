package com.cotani.task.throttle;

import java.time.Duration;

@SuppressWarnings("DeprecatedIsStillUsed")
public interface RateLimiter {

    /**
     * @deprecated Blocking acquisition violates the project async rules. Use {@link #tryAcquire()} and
     * reschedule on failure instead.
     */
    @Deprecated(forRemoval = true)
    void acquire() throws InterruptedException;

    boolean tryAcquire();

    /**
     * @deprecated Blocking acquisition violates the project async rules. Use {@link #tryAcquire()} and
     * reschedule on failure instead.
     */
    @Deprecated(forRemoval = true)
    boolean tryAcquire(Duration timeout) throws InterruptedException;

    /**
     * Returns the suggested non-negative delay to wait before the next {@link #tryAcquire()} attempt.
     * A zero duration means the caller may retry immediately.
     */
    default Duration retryDelay() {
        return Duration.ZERO;
    }
}

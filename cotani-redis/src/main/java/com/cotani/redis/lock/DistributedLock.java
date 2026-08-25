package com.cotani.redis.lock;

import com.cotani.AsyncCloseable;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Handle representing an acquired distributed lock.
 */
public interface DistributedLock extends AutoCloseable, AsyncCloseable {

    /**
     * Returns the locked key.
     *
     * @return lock key
     */
    LockKey key();

    /**
     * Returns the unique token identifying this client's ownership of the lock.
     *
     * @return ownership token
     */
    LockToken token();

    /**
     * Returns the lease duration granted upon lock acquisition.
     *
     * @return lease duration
     */
    Duration leaseTime();

    /**
     * Checks if this handle believes it currently holds the lock.
     *
     * @return true if held, false if released or expired
     */
    boolean isHeld();

    /**
     * Asynchronously releases this lock using atomic Lua evaluation to ensure
     * that only the owning token can delete the lock key.
     *
     * @return stage completing once released
     */
    CompletionStage<Void> releaseAsync();

    @Override
    default CompletionStage<Void> closeAsync() {
        return releaseAsync();
    }

    /**
     * Begins releasing this lock without blocking.
     *
     * <p>Use {@link #releaseAsync()} to observe completion and failures.
     */
    @Override
    void close();
}

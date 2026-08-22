package com.cotani.redis.lock;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Service for acquiring and executing work under distributed mutual exclusion locks across server clusters.
 */
public interface DistributedLockService {

    /**
     * Attempts to acquire a distributed lock immediately without waiting.
     *
     * @param key the lock key to acquire
     * @param leaseTime the maximum duration the lock can be held before auto-expiring
     * @return stage completing with the acquired lock handle if successful, or empty if already locked
     */
    CompletionStage<Optional<DistributedLock>> tryAcquireAsync(LockKey key, Duration leaseTime);

    /**
     * Attempts to acquire a distributed lock, retrying asynchronously until the wait timeout expires.
     *
     * @param key the lock key
     * @param leaseTime the maximum duration the lock can be held before auto-expiring
     * @param waitTimeout maximum time to keep attempting acquisition before failing
     * @return stage completing with the acquired lock handle
     */
    CompletionStage<DistributedLock> acquireAsync(LockKey key, Duration leaseTime, Duration waitTimeout);

    /**
     * Executes an asynchronous task safely under the protection of a distributed lock,
     * guaranteeing that the lock is released when the task completes (successfully or exceptionally).
     *
     * @param key the lock key
     * @param leaseTime maximum duration the lock is held
     * @param action supplier returning the stage to execute
     * @param <T> task result type
     * @return stage completing with the action's result
     */
    <T> CompletionStage<T> withLockAsync(LockKey key, Duration leaseTime, Supplier<CompletionStage<T>> action);

    /**
     * Executes an asynchronous task safely under a distributed lock with automatic lease renewal (Watchdog),
     * periodically extending the lock's expiration until the task completes.
     *
     * @param key the lock key
     * @param initialLeaseTime base duration before expiration (renewed periodically)
     * @param action supplier returning the stage to execute
     * @param <T> task result type
     * @return stage completing with the action's result
     */
    <T> CompletionStage<T> withWatchdogLockAsync(
            LockKey key, Duration initialLeaseTime, Supplier<CompletionStage<T>> action);
}

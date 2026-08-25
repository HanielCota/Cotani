package com.cotani.queue.api;

import java.util.concurrent.CompletionStage;

/** Asynchronous persistence boundary for queue state. */
public interface QueueRepository {
    /** Loads the current immutable queue snapshot without blocking the caller. */
    CompletionStage<QueueSnapshot> loadAsync();

    /**
     * Atomically persists a snapshot only when the stored revision equals {@code expectedRevision}.
     * The snapshot revision must be exactly {@code expectedRevision + 1}.
     *
     * <p>The implementation must not block the calling thread and must complete exceptionally
     * when the compare-and-save condition is not satisfied.
     */
    CompletionStage<Void> saveAsync(QueueSnapshot snapshot, long expectedRevision);
}

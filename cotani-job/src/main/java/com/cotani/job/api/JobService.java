package com.cotani.job.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.concurrent.CompletionStage;

/** Non-blocking service for durable, named asynchronous jobs. */
public interface JobService extends AsyncCloseable, AutoCloseable {
    /** Registers or replaces a handler before scheduling or recovering jobs. */
    void registerHandler(String name, JobHandler handler);

    /** Schedules a job and durably writes its recovery record before dispatch. */
    CompletionStage<JobHandle> scheduleAsync(JobRequest request);

    /**
     * Loads and dispatches up to {@link JobServiceOptions#maxRecoveryBatch()} pending Cotani job
     * records with registered handlers. Concurrent recovery calls are coalesced.
     */
    CompletionStage<List<JobHandle>> recoverPendingAsync();

    /**
     * Cancels a job and removes its durable recovery record when possible. If a handler is already
     * running, the returned stage completes after that invocation finishes and the record is removed.
     */
    CompletionStage<Boolean> cancelAsync(JobId jobId);

    /**
     * Stops new scheduling and cancels local timers. Pending durable records remain available for
     * recovery after a server restart.
     */
    @Override
    CompletionStage<Void> closeAsync();

    /** Starts asynchronous close. Pending records remain recoverable after shutdown. */
    @Override
    default void close() {
        closeAsync();
    }
}

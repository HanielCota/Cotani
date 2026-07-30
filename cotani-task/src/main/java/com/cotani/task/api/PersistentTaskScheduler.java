package com.cotani.task.api;

import com.cotani.task.persistence.PersistentTask;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/** Schedules crash-recoverable work with at-least-once execution semantics. */
public interface PersistentTaskScheduler {

    /**
     * Persists and schedules a task. Explicit cancellation removes its recovery record;
     * scheduler shutdown leaves the record pending for recovery.
     */
    SchedulerTask persistAndRun(String name, Duration delay, byte[] payload, Consumer<byte[]> executor);

    /** Loads pending records asynchronously on the scheduler's explicit async executor. */
    CompletionStage<List<PersistentTask>> recoverPendingTasksAsync();
}

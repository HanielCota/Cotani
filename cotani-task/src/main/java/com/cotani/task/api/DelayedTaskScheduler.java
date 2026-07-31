package com.cotani.task.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Schedules delayed and recurring asynchronous work without sleeping or blocking the caller. */
public interface DelayedTaskScheduler {
    SchedulerTask asyncLater(Runnable runnable, Duration delay);

    SchedulerTask asyncLater(String name, Runnable runnable, Duration delay);

    SchedulerTask asyncTimer(Runnable runnable, Duration initialDelay, Duration period);

    /**
     * Completes after {@code duration}. Cancelling the returned stage cancels the owned delayed
     * task on a best-effort basis.
     */
    default CompletionStage<Void> delayAsync(Duration duration) {
        Objects.requireNonNull(duration, "duration");

        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerTask pending = asyncLater("scheduler-delay", () -> future.complete(null), duration);
        var _ = future.whenComplete((_, _) -> pending.cancel());

        return future;
    }
}

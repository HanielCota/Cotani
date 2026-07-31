package com.cotani.task.api;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Executes named asynchronous work and dispatches value-producing work to an explicit target.
 *
 * <p>Calls never block the caller. A returned {@link SchedulerTask} provides best-effort
 * cancellation; a returned stage propagates task failures and entity-retirement failures.
 */
public interface AsyncTaskExecutor {
    SchedulerTask async(Runnable runnable);

    SchedulerTask async(String name, Runnable runnable);

    <T> CompletionStage<T> supply(ExecutionTarget target, String name, Supplier<T> supplier);

    /** Returns an executor adapter backed by this scheduler's explicit async execution policy. */
    Executor asyncExecutor();
}

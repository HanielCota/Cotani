package com.cotani.job.api;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous, Bukkit-free implementation of a named job.
 *
 * <p>The handler must be safe to retry with the same {@link JobExecutionContext#executionId()}.
 * A timeout requests that the observation stop waiting, but cannot forcibly stop external work;
 * the service therefore does not retry until the original stage has completed.
 */
@FunctionalInterface
public interface JobHandler {
    CompletionStage<Void> executeAsync(JobExecutionContext context);
}

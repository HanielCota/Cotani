package com.cotani.task.impl.dispatch;

import com.cotani.task.api.TaskContext;
import com.cotani.task.api.TaskContextHolder;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.metrics.TaskMetrics;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class TaskRunner {

    private final TaskExceptionHandler exceptionHandler;
    private final TaskMetrics metrics;

    public TaskRunner(TaskExceptionHandler exceptionHandler, TaskMetrics metrics) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public Runnable wrap(TaskMetadata metadata, Runnable runnable) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(runnable, "runnable");

        return () -> {
            var context = TaskContext.start(metadata);

            ScopedValue.where(TaskContextHolder.CURRENT, context).run(() -> {
                try {
                    runnable.run();
                    metrics.record(metadata, true, elapsed(context));
                } catch (Exception exception) {
                    metrics.record(metadata, false, elapsed(context));
                    exceptionHandler.handle(context, exception);

                    throw exception;
                }
            });
        };
    }

    public <T> void complete(TaskMetadata metadata, Supplier<T> supplier, CompletableFuture<T> future) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(future, "future");

        var context = TaskContext.start(metadata);

        try {
            future.complete(supplier.get());
            metrics.record(metadata, true, elapsed(context));
        } catch (Exception exception) {
            metrics.record(metadata, false, elapsed(context));
            exceptionHandler.handle(context, exception);
            future.completeExceptionally(new CompletionException(exception));
        }
    }

    private static Duration elapsed(TaskContext context) {
        return Duration.ofMillis(context.elapsedMillis());
    }
}

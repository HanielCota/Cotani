package com.cotani.task.internal.dispatch;

import com.cotani.api.InternalApi;
import com.cotani.task.api.TaskContext;
import com.cotani.task.api.TaskContextHolder;
import com.cotani.task.api.TaskExceptionHandler;
import com.cotani.task.api.TaskMetadata;
import com.cotani.task.exception.TaskExecutionException;
import com.cotani.task.metrics.TaskMetrics;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@InternalApi
public final class TaskRunner {
    private final TaskExceptionHandler exceptionHandler;
    private final TaskMetrics metrics;

    private TaskRunner(TaskExceptionHandler exceptionHandler, TaskMetrics metrics) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public static TaskRunner create(TaskExceptionHandler exceptionHandler, TaskMetrics metrics) {
        return new TaskRunner(exceptionHandler, metrics);
    }

    private static Duration elapsed(TaskContext context) {
        return Duration.ofMillis(context.elapsedMillis());
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

                    if (exception instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new TaskExecutionException(exception);
                }
            });
        };
    }

    public <T> void complete(TaskMetadata metadata, Supplier<T> supplier, CompletableFuture<T> future) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(supplier, "supplier");
        Objects.requireNonNull(future, "future");

        var context = TaskContext.start(metadata);
        ScopedValue.where(TaskContextHolder.CURRENT, context)
                .run(() -> completeWithinContext(context, supplier, future));
    }

    private <T> void completeWithinContext(TaskContext context, Supplier<T> supplier, CompletableFuture<T> future) {
        try {
            future.complete(supplier.get());
            metrics.record(context.metadata(), true, elapsed(context));
        } catch (Exception exception) {
            metrics.record(context.metadata(), false, elapsed(context));
            exceptionHandler.handle(context, exception);
            future.completeExceptionally(exception);
        }
    }
}

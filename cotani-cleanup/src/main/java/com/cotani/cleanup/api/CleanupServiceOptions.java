package com.cotani.cleanup.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Timeouts and backpressure limits for cleanup operations and notifications. */
public record CleanupServiceOptions(Duration operationTimeout, Duration eventTimeout, int maxPendingOperations) {
    public CleanupServiceOptions {
        Objects.requireNonNull(operationTimeout, "operationTimeout");
        Objects.requireNonNull(eventTimeout, "eventTimeout");
        if (operationTimeout.isZero() || operationTimeout.isNegative()) {
            throw new IllegalArgumentException("operationTimeout must be positive");
        }
        if (eventTimeout.isZero() || eventTimeout.isNegative()) {
            throw new IllegalArgumentException("eventTimeout must be positive");
        }
        if (maxPendingOperations <= 0 || maxPendingOperations > 10_000) {
            throw new IllegalArgumentException("maxPendingOperations must be between 1 and 10000");
        }
    }

    public static CleanupServiceOptions defaults() {
        return new CleanupServiceOptions(Duration.ofSeconds(30), Duration.ofSeconds(5), 8);
    }

    public CleanupServiceOptions withOperationTimeout(Duration timeout) {
        return new CleanupServiceOptions(timeout, eventTimeout, maxPendingOperations);
    }

    public CleanupServiceOptions withEventTimeout(Duration timeout) {
        return new CleanupServiceOptions(operationTimeout, timeout, maxPendingOperations);
    }

    public CleanupServiceOptions withMaxPendingOperations(int limit) {
        return new CleanupServiceOptions(operationTimeout, eventTimeout, limit);
    }

    public <T> CompletionStage<T> withOperationTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis(operationTimeout), TimeUnit.MILLISECONDS);
    }

    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis(eventTimeout), TimeUnit.MILLISECONDS);
    }

    private static long timeoutMillis(Duration duration) {
        return Math.max(1L, duration.toMillis());
    }
}

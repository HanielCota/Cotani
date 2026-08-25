package com.cotani.queue.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Operational limits and timeouts for a queue service. */
public record QueueServiceOptions(int maximumEntriesPerQueue, Duration repositoryTimeout, Duration eventTimeout) {
    public QueueServiceOptions(int maximumEntriesPerQueue, Duration repositoryTimeout) {
        this(maximumEntriesPerQueue, repositoryTimeout, Duration.ofSeconds(5));
    }

    public QueueServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(eventTimeout, "eventTimeout");
        if (maximumEntriesPerQueue < 1) {
            throw new IllegalArgumentException("maximumEntriesPerQueue must be positive");
        }
        validatePositive(repositoryTimeout, "repositoryTimeout");
        validatePositive(eventTimeout, "eventTimeout");
    }

    public static QueueServiceOptions defaults() {
        return new QueueServiceOptions(1_000, Duration.ofSeconds(10), Duration.ofSeconds(5));
    }

    public QueueServiceOptions withMaximumEntriesPerQueue(int maximumEntries) {
        return new QueueServiceOptions(maximumEntries, repositoryTimeout, eventTimeout);
    }

    public QueueServiceOptions withRepositoryTimeout(Duration timeout) {
        return new QueueServiceOptions(maximumEntriesPerQueue, timeout, eventTimeout);
    }

    public QueueServiceOptions withEventTimeout(Duration timeout) {
        return new QueueServiceOptions(maximumEntriesPerQueue, repositoryTimeout, timeout);
    }

    /**
     * Applies the repository deadline without requiring the supplied stage to be a
     * {@code CompletableFuture}. The source stage is not cancelled when the deadline expires.
     */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        return withTimeout(stage, repositoryTimeout, "repository");
    }

    /**
     * Applies the event deadline without requiring the supplied stage to be a
     * {@code CompletableFuture}. The source stage is not cancelled when the deadline expires.
     */
    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        return withTimeout(stage, eventTimeout, "event");
    }

    private static void validatePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static <T> CompletionStage<T> withTimeout(
            CompletionStage<T> stage, Duration timeout, String operationName) {
        Objects.requireNonNull(stage, "stage");
        var result = new CompletableFuture<T>();
        try {
            Objects.requireNonNull(
                    stage.whenComplete((value, failure) -> {
                        if (failure == null) {
                            result.complete(value);
                            return;
                        }
                        result.completeExceptionally(failure);
                    }),
                    "completion stage");
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
            return result;
        }
        var timeoutMillis = timeoutMillis(timeout);
        CompletableFuture.delayedExecutor(timeoutMillis, TimeUnit.MILLISECONDS, Runnable::run)
                .execute(() -> result.completeExceptionally(
                        new TimeoutException(operationName + " operation timed out after " + timeout)));
        return result;
    }

    private static long timeoutMillis(Duration timeout) {
        try {
            return Math.max(1, timeout.toMillis());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}

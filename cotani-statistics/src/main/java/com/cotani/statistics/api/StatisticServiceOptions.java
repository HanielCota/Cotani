package com.cotani.statistics.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits for statistic reads, events, retries and pending mutations. */
public record StatisticServiceOptions(
        Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout, int maxPendingMutations) {
    public StatisticServiceOptions(Duration repositoryTimeout, int maxMutationAttempts) {
        this(repositoryTimeout, maxMutationAttempts, Duration.ofSeconds(5), 10_000);
    }

    public StatisticServiceOptions(Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout) {
        this(repositoryTimeout, maxMutationAttempts, eventTimeout, 10_000);
    }

    public StatisticServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(eventTimeout, "eventTimeout");
        if (repositoryTimeout.isZero() || repositoryTimeout.isNegative()) {
            throw new IllegalArgumentException("repositoryTimeout must be positive");
        }
        if (eventTimeout.isZero() || eventTimeout.isNegative()) {
            throw new IllegalArgumentException("eventTimeout must be positive");
        }
        if (maxMutationAttempts <= 0 || maxMutationAttempts > 10) {
            throw new IllegalArgumentException("maxMutationAttempts must be between 1 and 10");
        }
        if (maxPendingMutations <= 0 || maxPendingMutations > 1_000_000) {
            throw new IllegalArgumentException("maxPendingMutations must be between 1 and 1000000");
        }
    }

    public static StatisticServiceOptions defaults() {
        return new StatisticServiceOptions(Duration.ofSeconds(10), 3, Duration.ofSeconds(5), 10_000);
    }

    public StatisticServiceOptions withRepositoryTimeout(Duration timeout) {
        return new StatisticServiceOptions(timeout, maxMutationAttempts, eventTimeout, maxPendingMutations);
    }

    public StatisticServiceOptions withMaxMutationAttempts(int attempts) {
        return new StatisticServiceOptions(repositoryTimeout, attempts, eventTimeout, maxPendingMutations);
    }

    public StatisticServiceOptions withEventTimeout(Duration timeout) {
        return new StatisticServiceOptions(repositoryTimeout, maxMutationAttempts, timeout, maxPendingMutations);
    }

    public StatisticServiceOptions withMaxPendingMutations(int pendingMutations) {
        return new StatisticServiceOptions(repositoryTimeout, maxMutationAttempts, eventTimeout, pendingMutations);
    }

    /** Applies a caller-visible timeout to a read without cancelling the repository operation. */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, repositoryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Applies a best-effort timeout to event delivery without cancelling the durable mutation. */
    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, eventTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

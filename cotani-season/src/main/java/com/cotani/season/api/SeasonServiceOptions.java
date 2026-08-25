package com.cotani.season.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits for season persistence, events and queued player mutations. */
public record SeasonServiceOptions(
        Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout, int maxPendingMutations) {
    public SeasonServiceOptions(Duration repositoryTimeout, int maxMutationAttempts) {
        this(repositoryTimeout, maxMutationAttempts, Duration.ofSeconds(5), 10_000);
    }

    public SeasonServiceOptions {
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

    public static SeasonServiceOptions defaults() {
        return new SeasonServiceOptions(Duration.ofSeconds(10), 3, Duration.ofSeconds(5), 10_000);
    }

    public SeasonServiceOptions withRepositoryTimeout(Duration timeout) {
        return new SeasonServiceOptions(timeout, maxMutationAttempts, eventTimeout, maxPendingMutations);
    }

    public SeasonServiceOptions withMaxMutationAttempts(int attempts) {
        return new SeasonServiceOptions(repositoryTimeout, attempts, eventTimeout, maxPendingMutations);
    }

    public SeasonServiceOptions withEventTimeout(Duration timeout) {
        return new SeasonServiceOptions(repositoryTimeout, maxMutationAttempts, timeout, maxPendingMutations);
    }

    public SeasonServiceOptions withMaxPendingMutations(int pendingMutations) {
        return new SeasonServiceOptions(repositoryTimeout, maxMutationAttempts, eventTimeout, pendingMutations);
    }

    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis(repositoryTimeout), TimeUnit.MILLISECONDS);
    }

    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis(eventTimeout), TimeUnit.MILLISECONDS);
    }

    private static long timeoutMillis(Duration timeout) {
        try {
            return Math.max(1L, timeout.toMillis());
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("timeout is too large", overflow);
        }
    }
}

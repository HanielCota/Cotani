package com.cotani.achievement.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits for achievement reads, events, retries and pending mutations. */
public record AchievementServiceOptions(
        Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout, int maxPendingMutations) {
    public AchievementServiceOptions(Duration repositoryTimeout, int maxMutationAttempts) {
        this(repositoryTimeout, maxMutationAttempts, Duration.ofSeconds(5), 10_000);
    }

    public AchievementServiceOptions(Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout) {
        this(repositoryTimeout, maxMutationAttempts, eventTimeout, 10_000);
    }

    public AchievementServiceOptions {
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

    public static AchievementServiceOptions defaults() {
        return new AchievementServiceOptions(Duration.ofSeconds(10), 3, Duration.ofSeconds(5), 10_000);
    }

    public AchievementServiceOptions withRepositoryTimeout(Duration timeout) {
        return new AchievementServiceOptions(timeout, maxMutationAttempts, eventTimeout, maxPendingMutations);
    }

    public AchievementServiceOptions withMaxMutationAttempts(int attempts) {
        return new AchievementServiceOptions(repositoryTimeout, attempts, eventTimeout, maxPendingMutations);
    }

    public AchievementServiceOptions withEventTimeout(Duration timeout) {
        return new AchievementServiceOptions(repositoryTimeout, maxMutationAttempts, timeout, maxPendingMutations);
    }

    public AchievementServiceOptions withMaxPendingMutations(int pendingMutations) {
        return new AchievementServiceOptions(repositoryTimeout, maxMutationAttempts, eventTimeout, pendingMutations);
    }

    /** Applies a caller-visible timeout without cancelling the underlying operation. */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, repositoryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Applies a best-effort timeout to event publication. */
    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, eventTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

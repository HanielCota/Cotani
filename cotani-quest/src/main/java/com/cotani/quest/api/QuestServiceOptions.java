package com.cotani.quest.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits for quest reads, event delivery, pending mutations and optimistic retries. */
public record QuestServiceOptions(
        Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout, int maxPendingMutations) {
    public QuestServiceOptions(Duration repositoryTimeout, int maxMutationAttempts) {
        this(repositoryTimeout, maxMutationAttempts, Duration.ofSeconds(5), 10_000);
    }

    public QuestServiceOptions(Duration repositoryTimeout, int maxMutationAttempts, Duration eventTimeout) {
        this(repositoryTimeout, maxMutationAttempts, eventTimeout, 10_000);
    }

    public QuestServiceOptions {
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

    public static QuestServiceOptions defaults() {
        return new QuestServiceOptions(Duration.ofSeconds(10), 3, Duration.ofSeconds(5), 10_000);
    }

    public QuestServiceOptions withRepositoryTimeout(Duration timeout) {
        return new QuestServiceOptions(timeout, maxMutationAttempts, eventTimeout, maxPendingMutations);
    }

    public QuestServiceOptions withMaxMutationAttempts(int attempts) {
        return new QuestServiceOptions(repositoryTimeout, attempts, eventTimeout, maxPendingMutations);
    }

    public QuestServiceOptions withEventTimeout(Duration timeout) {
        return new QuestServiceOptions(repositoryTimeout, maxMutationAttempts, timeout, maxPendingMutations);
    }

    public QuestServiceOptions withMaxPendingMutations(int pendingMutations) {
        return new QuestServiceOptions(repositoryTimeout, maxMutationAttempts, eventTimeout, pendingMutations);
    }

    /** Applies a caller-visible timeout to a read without cancelling the repository operation. */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, repositoryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    /** Applies a best-effort timeout to event publication without cancelling the durable mutation. */
    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, eventTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

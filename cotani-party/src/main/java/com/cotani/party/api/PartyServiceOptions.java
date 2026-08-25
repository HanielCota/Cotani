package com.cotani.party.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational options for repository-backed party services. */
public record PartyServiceOptions(Duration repositoryTimeout, Duration eventTimeout) {
    public PartyServiceOptions(Duration repositoryTimeout) {
        this(repositoryTimeout, Duration.ofSeconds(5));
    }

    public PartyServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(eventTimeout, "eventTimeout");
        if (repositoryTimeout.isZero() || repositoryTimeout.isNegative()) {
            throw new IllegalArgumentException("repositoryTimeout must be positive");
        }
        if (eventTimeout.isZero() || eventTimeout.isNegative()) {
            throw new IllegalArgumentException("eventTimeout must be positive");
        }
    }

    public static PartyServiceOptions defaults() {
        return new PartyServiceOptions(Duration.ofSeconds(10), Duration.ofSeconds(5));
    }

    public PartyServiceOptions withRepositoryTimeout(Duration timeout) {
        return new PartyServiceOptions(timeout, eventTimeout);
    }

    public PartyServiceOptions withEventTimeout(Duration timeout) {
        return new PartyServiceOptions(repositoryTimeout, timeout);
    }

    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(repositoryTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(eventTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private long repositoryTimeoutMillis() {
        return Math.max(1, repositoryTimeout.toMillis());
    }

    private long eventTimeoutMillis() {
        return Math.max(1, eventTimeout.toMillis());
    }
}

package com.cotani.friend.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Timeouts for repository and event-bus operations. */
public record FriendServiceOptions(Duration repositoryTimeout, Duration eventTimeout) {
    public FriendServiceOptions(Duration repositoryTimeout) {
        this(repositoryTimeout, Duration.ofSeconds(5));
    }

    public FriendServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(eventTimeout, "eventTimeout");
        validatePositive(repositoryTimeout, "repositoryTimeout");
        validatePositive(eventTimeout, "eventTimeout");
    }

    public static FriendServiceOptions defaults() {
        return new FriendServiceOptions(Duration.ofSeconds(10), Duration.ofSeconds(5));
    }

    public FriendServiceOptions withRepositoryTimeout(Duration timeout) {
        return new FriendServiceOptions(timeout, eventTimeout);
    }

    public FriendServiceOptions withEventTimeout(Duration timeout) {
        return new FriendServiceOptions(repositoryTimeout, timeout);
    }

    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(repositoryTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    public <T> CompletionStage<T> withEventTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(eventTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    private static void validatePositive(Duration duration, String name) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private long repositoryTimeoutMillis() {
        return Math.max(1, repositoryTimeout.toMillis());
    }

    private long eventTimeoutMillis() {
        return Math.max(1, eventTimeout.toMillis());
    }
}

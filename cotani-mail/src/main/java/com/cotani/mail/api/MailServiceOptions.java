package com.cotani.mail.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits and repository timeout for a mail service. */
public record MailServiceOptions(int maxPageSize, Duration defaultTimeToLive, Duration repositoryTimeout) {
    public MailServiceOptions {
        Objects.requireNonNull(defaultTimeToLive, "defaultTimeToLive");
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        if (maxPageSize <= 0) {
            throw new IllegalArgumentException("maxPageSize must be positive");
        }
        if (defaultTimeToLive.isZero() || defaultTimeToLive.isNegative()) {
            throw new IllegalArgumentException("defaultTimeToLive must be positive");
        }
        if (repositoryTimeout.isZero() || repositoryTimeout.isNegative()) {
            throw new IllegalArgumentException("repositoryTimeout must be positive");
        }
    }

    public static MailServiceOptions defaults() {
        return new MailServiceOptions(50, Duration.ofDays(30), Duration.ofSeconds(10));
    }

    public MailServiceOptions withMaxPageSize(int maximum) {
        return new MailServiceOptions(maximum, defaultTimeToLive, repositoryTimeout);
    }

    public MailServiceOptions withDefaultTimeToLive(Duration ttl) {
        return new MailServiceOptions(maxPageSize, ttl, repositoryTimeout);
    }

    public MailServiceOptions withRepositoryTimeout(Duration timeout) {
        return new MailServiceOptions(maxPageSize, defaultTimeToLive, timeout);
    }

    /**
     * Applies the timeout to the visible stage without cancelling the repository operation.
     *
     * <p>Services use a separate durable barrier for mutation ordering and shutdown.
     */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, repositoryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

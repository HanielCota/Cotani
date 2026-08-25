package com.cotani.punishment.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Options for repository-backed punishment operations. */
public record PunishmentServiceOptions(Duration repositoryTimeout) {
    public PunishmentServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        if (repositoryTimeout.isZero() || repositoryTimeout.isNegative()) {
            throw new IllegalArgumentException("repositoryTimeout must be positive");
        }
    }

    public static PunishmentServiceOptions defaults() {
        return new PunishmentServiceOptions(Duration.ofSeconds(10));
    }

    public <T> CompletionStage<T> withTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().orTimeout(toMillis(), TimeUnit.MILLISECONDS);
    }

    private long toMillis() {
        return Math.max(1, repositoryTimeout.toMillis());
    }
}

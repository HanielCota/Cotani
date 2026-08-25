package com.cotani.location.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits and timeout for a location service. */
public record LocationServiceOptions(int maxHomesPerPlayer, Duration repositoryTimeout) {
    public LocationServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        if (maxHomesPerPlayer <= 0) {
            throw new IllegalArgumentException("maxHomesPerPlayer must be positive");
        }
        if (repositoryTimeout.isZero() || repositoryTimeout.isNegative()) {
            throw new IllegalArgumentException("repositoryTimeout must be positive");
        }
    }

    public static LocationServiceOptions defaults() {
        return new LocationServiceOptions(3, Duration.ofSeconds(10));
    }

    /** Returns these options with a different home limit. */
    public LocationServiceOptions withMaxHomesPerPlayer(int maximum) {
        return new LocationServiceOptions(maximum, repositoryTimeout);
    }

    /** Returns these options with a different repository timeout. */
    public LocationServiceOptions withRepositoryTimeout(Duration timeout) {
        return new LocationServiceOptions(maxHomesPerPlayer, timeout);
    }

    /**
     * Applies the configured timeout to the caller-facing stage.
     *
     * <p>This does not cancel the underlying operation. The location service therefore uses this only for the
     * visible result and keeps a separate durable barrier for queue ordering and shutdown.
     */
    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, repositoryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

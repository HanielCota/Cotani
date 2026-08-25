package com.cotani.locale.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.jspecify.annotations.NullMarked;

/** Options controlling repository-backed locale operations. */
@NullMarked
public record LocaleServiceOptions(Duration persistenceTimeout) {
    private static final Duration DEFAULT_PERSISTENCE_TIMEOUT = Duration.ofSeconds(10);

    public LocaleServiceOptions {
        Objects.requireNonNull(persistenceTimeout, "persistenceTimeout");
        if (persistenceTimeout.isNegative() || persistenceTimeout.isZero()) {
            throw new IllegalArgumentException("persistenceTimeout must be positive");
        }
        if (persistenceTimeout.toMillis() < 1) {
            throw new IllegalArgumentException("persistenceTimeout must be at least 1 millisecond");
        }
    }

    /** Returns the default ten-second repository timeout. */
    public static LocaleServiceOptions defaults() {
        return new LocaleServiceOptions(DEFAULT_PERSISTENCE_TIMEOUT);
    }

    /** Applies the configured timeout to a repository stage. */
    public <T> CompletionStage<T> withTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().orTimeout(toMillis(), TimeUnit.MILLISECONDS);
    }

    private long toMillis() {
        return persistenceTimeout.toMillis();
    }
}

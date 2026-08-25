package com.cotani.ranking.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational bounds for ranking queries. */
public record RankingServiceOptions(Duration queryTimeout, int maxPendingQueries) {
    public RankingServiceOptions {
        Objects.requireNonNull(queryTimeout, "queryTimeout");
        if (queryTimeout.isZero() || queryTimeout.isNegative()) {
            throw new IllegalArgumentException("queryTimeout must be positive");
        }
        if (maxPendingQueries <= 0 || maxPendingQueries > 1_000_000) {
            throw new IllegalArgumentException("maxPendingQueries must be between 1 and 1000000");
        }
    }

    public static RankingServiceOptions defaults() {
        return new RankingServiceOptions(Duration.ofSeconds(5), 1_000);
    }

    /**
     * Applies a caller-visible timeout without blocking the calling thread.
     *
     * <p>The timeout is applied to a copy of the stage and therefore does not cancel the original
     * operation. Callers that use this method must keep the original operation accounted for until
     * it completes.
     */
    public <T> CompletionStage<T> withQueryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        long timeoutMillis = Math.max(1L, queryTimeout.toMillis());
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
    }
}

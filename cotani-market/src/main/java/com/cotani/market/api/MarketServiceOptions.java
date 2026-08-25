package com.cotani.market.api;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Operational limits for marketplace queries, persistence and settlement. */
public record MarketServiceOptions(
        Duration repositoryTimeout,
        Duration purchaseReservationTimeout,
        Duration settlementTimeout,
        int maxPageSize,
        int maxPendingRecovery) {
    public MarketServiceOptions(
            Duration repositoryTimeout, Duration settlementTimeout, int maxPageSize, int maxPendingRecovery) {
        this(repositoryTimeout, repositoryTimeout, settlementTimeout, maxPageSize, maxPendingRecovery);
    }

    public MarketServiceOptions {
        Objects.requireNonNull(repositoryTimeout, "repositoryTimeout");
        Objects.requireNonNull(purchaseReservationTimeout, "purchaseReservationTimeout");
        Objects.requireNonNull(settlementTimeout, "settlementTimeout");
        if (repositoryTimeout.isZero()
                || repositoryTimeout.isNegative()
                || purchaseReservationTimeout.isZero()
                || purchaseReservationTimeout.isNegative()
                || settlementTimeout.isZero()
                || settlementTimeout.isNegative()) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (maxPageSize <= 0 || maxPageSize > 1_000 || maxPendingRecovery <= 0 || maxPendingRecovery > 1_000) {
            throw new IllegalArgumentException("limits must be between 1 and 1000");
        }
    }

    public static MarketServiceOptions defaults() {
        return new MarketServiceOptions(Duration.ofSeconds(5), Duration.ofSeconds(5), Duration.ofSeconds(30), 50, 100);
    }

    public <T> CompletionStage<T> withRepositoryTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis(repositoryTimeout), TimeUnit.MILLISECONDS);
    }

    public <T> CompletionStage<T> withSettlementTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture().copy().orTimeout(timeoutMillis(settlementTimeout), TimeUnit.MILLISECONDS);
    }

    public <T> CompletionStage<T> withPurchaseReservationTimeout(CompletionStage<T> stage) {
        Objects.requireNonNull(stage, "stage");
        return stage.toCompletableFuture()
                .copy()
                .orTimeout(timeoutMillis(purchaseReservationTimeout), TimeUnit.MILLISECONDS);
    }

    private static long timeoutMillis(Duration duration) {
        return Math.max(1L, duration.toMillis());
    }
}

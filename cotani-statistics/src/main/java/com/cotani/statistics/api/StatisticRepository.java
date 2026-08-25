package com.cotani.statistics.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Bukkit-free persistence SPI for atomic statistic increments and bounded rankings. */
public interface StatisticRepository {
    /** Loads one statistic; absence means the player has not recorded it yet. */
    CompletionStage<Optional<StatisticEntry>> findAsync(UUID playerId, StatisticId statisticId);

    /** Atomically increments a statistic and returns the exact previous and current values. */
    CompletionStage<StatisticUpdate> incrementAsync(
            UUID playerId, StatisticId statisticId, long amount, Instant updatedAt);

    /**
     * Atomically increments a statistic with a durable idempotency key.
     *
     * <p>Implementations must persist the idempotency result with the increment and return
     * {@link StatisticUpdate#newlyApplied()} as {@code false} when the same logical operation is
     * replayed. Repositories without idempotency support must reject this operation.
     */
    default CompletionStage<StatisticUpdate> incrementIdempotentlyAsync(
            UUID playerId, StatisticId statisticId, long amount, Instant updatedAt, StatisticOperationId operationId) {
        return java.util.concurrent.CompletableFuture.failedFuture(
                new UnsupportedOperationException("Repository does not support idempotent increments"));
    }

    /** Returns a deterministic, value-descending ranking limited by the caller. */
    CompletionStage<List<StatisticRankEntry>> topAsync(StatisticId statisticId, int limit);
}

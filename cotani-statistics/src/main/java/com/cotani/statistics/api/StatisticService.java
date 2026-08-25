package com.cotani.statistics.api;

import com.cotani.AsyncCloseable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous player statistic increments, reads and bounded rankings. */
public interface StatisticService extends AsyncCloseable, AutoCloseable {
    /** Loads one statistic after previously accepted mutations for the same player have settled. */
    CompletionStage<Optional<StatisticEntry>> findAsync(UUID playerId, StatisticId statisticId);

    /**
     * Atomically increments a statistic without retaining an idempotency ledger entry.
     *
     * <p>Use the operation-id overload when the caller may need to retry an unknown outcome.
     */
    CompletionStage<StatisticEntry> incrementAsync(UUID playerId, StatisticId statisticId, long amount);

    /**
     * Atomically increments a statistic and publishes a best-effort domain event.
     *
     * <p>Reuse the same operation id when retrying after a timeout or an unknown outcome.
     */
    CompletionStage<StatisticEntry> incrementAsync(
            UUID playerId, StatisticId statisticId, long amount, StatisticOperationId operationId);

    /** Loads a bounded deterministic ranking. */
    CompletionStage<StatisticRanking> topAsync(StatisticId statisticId, int limit);

    /** Starts asynchronous shutdown and rejects new increments. */
    @Override
    void close();
}

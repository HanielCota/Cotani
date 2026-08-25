package com.cotani.ranking.api;

import com.cotani.AsyncCloseable;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous named ranking views backed by a caller-owned statistics service.
 *
 * <p>Definitions are process-local and must be registered again after a restart. This service does
 * not own or close the supplied statistics service. Ranking queries never access Bukkit or Paper
 * objects and return immutable snapshots.
 */
public interface RankingService extends AsyncCloseable, AutoCloseable {
    /** Registers a ranking definition; duplicate ids are rejected. */
    void register(RankingDefinition definition);

    /** Returns a registered definition, if present. */
    Optional<RankingDefinition> findDefinition(RankingId rankingId);

    /**
     * Loads a bounded immutable snapshot ordered by value descending and UUID ascending.
     *
     * <p>The returned stage can fail with {@link java.util.concurrent.TimeoutException} when the
     * configured visible timeout elapses. That timeout does not cancel the underlying statistics
     * query; the configured pending-query limit remains occupied until the underlying query ends.
     */
    CompletionStage<RankingSnapshot> topAsync(RankingId rankingId, int limit);

    /**
     * Begins asynchronous shutdown, rejects new queries and registrations, and completes after
     * admitted underlying statistics queries finish.
     */
    @Override
    CompletionStage<Void> closeAsync();

    /** Starts asynchronous shutdown and rejects new queries and registrations. */
    @Override
    void close();
}

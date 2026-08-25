package com.cotani.location.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Asynchronous CRUD use cases for player homes and global warps.
 *
 * <p>Methods never block. A successful mutation means its repository operation completed and the new value is
 * visible to subsequent reads. Repository timeout failures are reported to the mutation caller, while the service
 * keeps its internal mutation barrier until the underlying operation has actually completed.
 */
public interface LocationService extends AsyncCloseable, AutoCloseable {
    /** Finds one home, returning empty when it is absent. */
    CompletionStage<Optional<Home>> findHomeAsync(UUID ownerId, LocationName name);

    /** Lists the owner's homes in deterministic name order. */
    CompletionStage<List<Home>> homesAsync(UUID ownerId);

    /** Creates or replaces one home, subject to the configured per-player limit. */
    CompletionStage<Home> setHomeAsync(UUID ownerId, LocationName name, LocationPosition position);

    /** Deletes one home, failing with {@link HomeNotFoundException} when absent. */
    CompletionStage<Void> deleteHomeAsync(UUID ownerId, LocationName name);

    /** Finds one warp, returning empty when it is absent. */
    CompletionStage<Optional<Warp>> findWarpAsync(LocationName name);

    /** Lists all warps in deterministic name order. */
    CompletionStage<List<Warp>> warpsAsync();

    /** Creates or replaces one global warp. */
    CompletionStage<Warp> setWarpAsync(LocationName name, LocationPosition position);

    /** Deletes one warp, failing with {@link WarpNotFoundException} when absent. */
    CompletionStage<Void> deleteWarpAsync(LocationName name);

    /**
     * Starts asynchronous shutdown, rejects new mutations, and completes after accepted mutations finish.
     *
     * <p>Use {@link #closeAsync()} when shutdown failures must be composed into the plugin lifecycle.
     */
    @Override
    void close();
}

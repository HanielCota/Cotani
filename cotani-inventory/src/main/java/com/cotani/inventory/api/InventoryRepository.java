package com.cotani.inventory.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

/**
 * Storage repository for saving, querying, and auditing historical inventory snapshots.
 */
@NullMarked
public interface InventoryRepository {

    /**
     * Persists an inventory snapshot into storage.
     *
     * @param snapshot snapshot to save
     * @return completion stage completed upon save
     */
    CompletionStage<Void> saveSnapshotAsync(InventorySnapshot snapshot);

    /**
     * Finds the most recent saved snapshot for a player.
     *
     * @param playerId player unique identifier
     * @return completion stage yielding the latest snapshot if found
     */
    CompletionStage<Optional<InventorySnapshot>> findLatestAsync(UUID playerId);

    /**
     * Retrieves historical snapshots for a player ordered by timestamp descending.
     *
     * @param playerId player unique identifier
     * @param limit maximum snapshots to retrieve
     * @return completion stage yielding list of historical snapshots
     */
    CompletionStage<List<InventorySnapshot>> findHistoryAsync(UUID playerId, int limit);

    /**
     * Retrieves a specific snapshot by player UUID and creation timestamp.
     *
     * @param playerId player unique identifier
     * @param createdAt snapshot epoch millisecond timestamp
     * @return completion stage yielding the snapshot if found
     */
    CompletionStage<Optional<InventorySnapshot>> findByIdAsync(UUID playerId, long createdAt);
}

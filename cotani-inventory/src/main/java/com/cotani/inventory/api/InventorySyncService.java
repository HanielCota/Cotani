package com.cotani.inventory.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Public service interface for capturing, applying, saving, restoring, and
 * coordinating cross-server synchronization of player inventories and player states.
 */
@NullMarked
public interface InventorySyncService {

    /**
     * Captures an immutable snapshot of the player's current inventory, stats, and effects.
     * Safely executes on the player's entity thread.
     *
     * @param player player to capture
     * @return completion stage yielding the captured snapshot
     */
    CompletionStage<InventorySnapshot> captureAsync(Player player);

    /**
     * Applies an inventory snapshot onto the player with specific sync options.
     * Safely executes on the player's entity thread.
     *
     * @param player target player
     * @param snapshot snapshot to apply
     * @param options synchronization options controlling which sections are updated
     * @return completion stage completed when applied
     */
    CompletionStage<Void> applyAsync(Player player, InventorySnapshot snapshot, InventorySyncOptions options);

    /**
     * Captures, mutates and applies a snapshot on the player's entity thread.
     *
     * <p>The mutation function is invoked on the entity thread and must not retain or publish
     * Bukkit objects. This operation is useful for atomic-looking item deliveries that must not
     * capture a live player in an asynchronous continuation.
     *
     * @param playerId target player UUID
     * @param mutation entity-thread snapshot mutation
     * @param options sections to apply
     * @return completion stage completed after the mutation is applied
     */
    default CompletionStage<Void> mutateAsync(
            UUID playerId, UnaryOperator<InventorySnapshot> mutation, InventorySyncOptions options) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(options, "options");
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This inventory service does not support entity-thread mutation"));
    }

    /**
     * Applies an inventory snapshot onto the player with all sections enabled.
     *
     * @param player target player
     * @param snapshot snapshot to apply
     * @return completion stage completed when applied
     */
    default CompletionStage<Void> applyAsync(Player player, InventorySnapshot snapshot) {
        return applyAsync(player, snapshot, InventorySyncOptions.all());
    }

    /**
     * Captures the player's current state and saves it asynchronously into the storage repository.
     *
     * @param player target player
     * @return completion stage yielding the saved snapshot
     */
    CompletionStage<InventorySnapshot> saveAsync(Player player);

    /**
     * Loads the latest saved snapshot for a player from the repository.
     *
     * @param playerId player unique identifier
     * @return completion stage yielding the snapshot if found
     */
    CompletionStage<Optional<InventorySnapshot>> loadLatestAsync(UUID playerId);

    /**
     * Loads the latest saved snapshot for the player and applies it to them.
     *
     * @param player target player
     * @param options synchronization options
     * @return completion stage yielding the applied snapshot if found
     */
    CompletionStage<Optional<InventorySnapshot>> loadAndApplyAsync(Player player, InventorySyncOptions options);

    /**
     * Loads the latest saved snapshot for the player and applies it with all options enabled.
     *
     * @param player target player
     * @return completion stage yielding the applied snapshot if found
     */
    default CompletionStage<Optional<InventorySnapshot>> loadAndApplyAsync(Player player) {
        return loadAndApplyAsync(player, InventorySyncOptions.all());
    }

    /**
     * Retrieves historical snapshots for audit or rollback purposes.
     *
     * @param playerId player unique identifier
     * @param limit maximum snapshots to retrieve
     * @return completion stage yielding historical snapshots
     */
    CompletionStage<List<InventorySnapshot>> historyAsync(UUID playerId, int limit);

    /**
     * Restores a specific historical snapshot onto a target player.
     *
     * @param player target player
     * @param snapshotTimestamp timestamp of the historical snapshot
     * @param options synchronization options
     * @return completion stage yielding true if found and restored, false otherwise
     */
    CompletionStage<Boolean> rollbackAsync(Player player, long snapshotTimestamp, InventorySyncOptions options);

    /**
     * Restores a specific historical snapshot onto a target player with all options enabled.
     *
     * @param player target player
     * @param snapshotTimestamp timestamp of the historical snapshot
     * @return completion stage yielding true if found and restored, false otherwise
     */
    default CompletionStage<Boolean> rollbackAsync(Player player, long snapshotTimestamp) {
        return rollbackAsync(player, snapshotTimestamp, InventorySyncOptions.all());
    }

    /**
     * Acquires a cross-server transfer lock for a player switching servers.
     *
     * @param playerId player unique identifier
     * @param lockDuration duration of the transfer lock
     * @return completion stage yielding the owned lease if acquired, empty if already in transit
     */
    CompletionStage<Optional<TransferLease>> beginTransferAsync(UUID playerId, Duration lockDuration);

    /**
     * Completes a cross-server transfer, releasing the network lock.
     *
     * @param lease owned lease returned by {@link #beginTransferAsync(UUID, Duration)}
     * @return completion stage completed when released
     */
    CompletionStage<Void> completeTransferAsync(TransferLease lease);
}

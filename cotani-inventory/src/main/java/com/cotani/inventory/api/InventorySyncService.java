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
     * The player is resolved only on its owning entity thread.
     *
     * @param playerId player UUID to capture
     * @return completion stage yielding the captured snapshot
     */
    CompletionStage<InventorySnapshot> captureAsync(UUID playerId);

    /**
     * Compatibility bridge for callers that already run on the player's owning thread.
     * Prefer {@link #captureAsync(UUID)} in asynchronous code.
     *
     * @param player player to capture
     * @return completion stage yielding the captured snapshot
     * @deprecated capture the UUID at the platform boundary and call {@link #captureAsync(UUID)}
     */
    @Deprecated
    default CompletionStage<InventorySnapshot> captureAsync(Player player) {
        Objects.requireNonNull(player, "player");
        return captureAsync(player.getUniqueId());
    }

    /**
     * Applies an inventory snapshot onto the player with specific sync options.
     * Safely executes on the player's entity thread after resolving the UUID at execution time.
     *
     * @param playerId target player UUID
     * @param snapshot snapshot to apply
     * @param options synchronization options controlling which sections are updated
     * @return completion stage completed when applied
     */
    CompletionStage<Void> applyAsync(UUID playerId, InventorySnapshot snapshot, InventorySyncOptions options);

    /**
     * Compatibility bridge for callers that already hold the live player at the platform boundary.
     * Prefer {@link #applyAsync(UUID, InventorySnapshot, InventorySyncOptions)} in asynchronous code.
     *
     * @param player target player
     * @param snapshot snapshot to apply
     * @param options synchronization options controlling which sections are updated
     * @return completion stage completed when applied
     * @deprecated capture the UUID at the platform boundary and call the UUID overload
     */
    @Deprecated
    default CompletionStage<Void> applyAsync(Player player, InventorySnapshot snapshot, InventorySyncOptions options) {
        Objects.requireNonNull(player, "player");
        return applyAsync(player.getUniqueId(), snapshot, options);
    }

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
    @Deprecated
    default CompletionStage<Void> applyAsync(Player player, InventorySnapshot snapshot) {
        return applyAsync(player, snapshot, InventorySyncOptions.all());
    }

    /**
     * Captures the player's current state on its entity thread and saves it asynchronously into
     * the storage repository.
     *
     * @param playerId target player UUID
     * @return completion stage yielding the saved snapshot
     */
    CompletionStage<InventorySnapshot> saveAsync(UUID playerId);

    /**
     * Compatibility bridge for callers at the platform boundary.
     *
     * @param player target player
     * @return completion stage yielding the saved snapshot
     * @deprecated capture the UUID at the platform boundary and call {@link #saveAsync(UUID)}
     */
    @Deprecated
    default CompletionStage<InventorySnapshot> saveAsync(Player player) {
        Objects.requireNonNull(player, "player");
        return saveAsync(player.getUniqueId());
    }

    /**
     * Loads the latest saved snapshot for a player from the repository.
     *
     * @param playerId player unique identifier
     * @return completion stage yielding the snapshot if found
     */
    CompletionStage<Optional<InventorySnapshot>> loadLatestAsync(UUID playerId);

    /**
     * Loads the latest saved snapshot for the player UUID and applies it to the online player.
     * The repository lookup runs asynchronously and the application returns to the player's
     * entity thread before touching Bukkit state.
     *
     * @param playerId target player UUID
     * @param options synchronization options
     * @return completion stage yielding the applied snapshot if found
     */
    CompletionStage<Optional<InventorySnapshot>> loadAndApplyAsync(UUID playerId, InventorySyncOptions options);

    /**
     * Compatibility bridge for callers at the platform boundary.
     *
     * @param player target player
     * @param options synchronization options
     * @return completion stage yielding the applied snapshot if found
     * @deprecated capture the UUID at the platform boundary and call the UUID overload
     */
    @Deprecated
    default CompletionStage<Optional<InventorySnapshot>> loadAndApplyAsync(
            Player player, InventorySyncOptions options) {
        Objects.requireNonNull(player, "player");
        return loadAndApplyAsync(player.getUniqueId(), options);
    }

    /**
     * Loads the latest saved snapshot for the player and applies it with all options enabled.
     *
     * @param player target player
     * @return completion stage yielding the applied snapshot if found
     */
    @Deprecated
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
     * Restores a specific historical snapshot onto the online player identified by the UUID.
     * The repository lookup runs asynchronously and the restoration returns to the player's
     * entity thread before touching Bukkit state.
     *
     * @param playerId target player UUID
     * @param snapshotTimestamp timestamp of the historical snapshot
     * @param options synchronization options
     * @return completion stage yielding true if found and restored, false otherwise
     */
    CompletionStage<Boolean> rollbackAsync(UUID playerId, long snapshotTimestamp, InventorySyncOptions options);

    /**
     * Compatibility bridge for callers at the platform boundary.
     *
     * @param player target player
     * @param snapshotTimestamp timestamp of the historical snapshot
     * @param options synchronization options
     * @return completion stage yielding true if found and restored, false otherwise
     * @deprecated capture the UUID at the platform boundary and call the UUID overload
     */
    @Deprecated
    default CompletionStage<Boolean> rollbackAsync(
            Player player, long snapshotTimestamp, InventorySyncOptions options) {
        Objects.requireNonNull(player, "player");
        return rollbackAsync(player.getUniqueId(), snapshotTimestamp, options);
    }

    /**
     * Restores a historical snapshot with all synchronization options enabled.
     *
     * @param playerId target player UUID
     * @param snapshotTimestamp timestamp of the historical snapshot
     * @return completion stage yielding true if found and restored, false otherwise
     */
    default CompletionStage<Boolean> rollbackAsync(UUID playerId, long snapshotTimestamp) {
        return rollbackAsync(playerId, snapshotTimestamp, InventorySyncOptions.all());
    }

    /**
     * Restores a specific historical snapshot onto a target player with all options enabled.
     *
     * @param player target player
     * @param snapshotTimestamp timestamp of the historical snapshot
     * @return completion stage yielding true if found and restored, false otherwise
     */
    @Deprecated
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

package com.cotani.examples.showcase;

import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;

/** Minimal repository used only to keep the showcase self-contained. Production uses SQL storage. */
@NullMarked
final class ShowcaseInventoryRepository implements InventoryRepository {
    private final ConcurrentHashMap<UUID, InventorySnapshot> latest = new ConcurrentHashMap<>();

    @Override
    public CompletionStage<Void> saveSnapshotAsync(InventorySnapshot snapshot) {
        latest.put(snapshot.playerId(), snapshot);
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> findLatestAsync(UUID playerId) {
        return CompletableFuture.completedFuture(Optional.ofNullable(latest.get(playerId)));
    }

    @Override
    public CompletionStage<List<InventorySnapshot>> findHistoryAsync(UUID playerId, int limit) {
        return CompletableFuture.completedFuture(
                latest.containsKey(playerId) ? List.of(latest.get(playerId)) : List.of());
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> findByIdAsync(UUID playerId, long createdAt) {
        var snapshot = latest.get(playerId);
        return CompletableFuture.completedFuture(
                snapshot != null && snapshot.createdAt() == createdAt ? Optional.of(snapshot) : Optional.empty());
    }
}

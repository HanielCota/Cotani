package com.cotani.inventory.internal.repository;

import com.cotani.api.InternalApi;
import com.cotani.inventory.api.InventoryRepository;
import com.cotani.inventory.api.InventorySerializer;
import com.cotani.inventory.api.InventorySnapshot;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.repository.CotaniRepository;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

/**
 * Storage-backed implementation of {@link InventoryRepository}.
 */
@InternalApi
@NullMarked
public final class StorageInventoryRepository extends CotaniRepository implements InventoryRepository {

    private static final String TABLE = "cotani_inventory_snapshots";
    private static final String PLAYER_ID_COL = "player_id";
    private static final String VERSION_COL = "version";
    private static final String CREATED_AT_COL = "created_at";
    private static final String SNAPSHOT_DATA_COL = "snapshot_data";

    private final InventorySerializer serializer;

    public StorageInventoryRepository(CotaniStorage storage, InventorySerializer serializer) {
        super(storage);
        this.serializer = Objects.requireNonNull(serializer, "serializer");
    }

    @Override
    public CompletionStage<Void> saveSnapshotAsync(InventorySnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        String payload = serializer.toBase64(snapshot);

        return table(TABLE)
                .upsert()
                .value(PLAYER_ID_COL, snapshot.playerId())
                .value(VERSION_COL, snapshot.version())
                .value(CREATED_AT_COL, snapshot.createdAt())
                .value(SNAPSHOT_DATA_COL, payload)
                .conflict(PLAYER_ID_COL, CREATED_AT_COL)
                .update(VERSION_COL, SNAPSHOT_DATA_COL)
                .execute();
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> findLatestAsync(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");

        return table(TABLE)
                .select()
                .where(PLAYER_ID_COL, playerId)
                .orderByDesc(CREATED_AT_COL)
                .limit(1)
                .one(row -> {
                    String base64 = row.getString(SNAPSHOT_DATA_COL);
                    return serializer.fromBase64(base64);
                });
    }

    @Override
    public CompletionStage<List<InventorySnapshot>> findHistoryAsync(UUID playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }

        return table(TABLE)
                .select()
                .where(PLAYER_ID_COL, playerId)
                .orderByDesc(CREATED_AT_COL)
                .limit(limit)
                .list(row -> {
                    String base64 = row.getString(SNAPSHOT_DATA_COL);
                    return serializer.fromBase64(base64);
                });
    }

    @Override
    public CompletionStage<Optional<InventorySnapshot>> findByIdAsync(UUID playerId, long createdAt) {
        Objects.requireNonNull(playerId, "playerId");

        return table(TABLE)
                .select()
                .where(PLAYER_ID_COL, playerId)
                .where(CREATED_AT_COL, createdAt)
                .limit(1)
                .one(row -> {
                    String base64 = row.getString(SNAPSHOT_DATA_COL);
                    return serializer.fromBase64(base64);
                });
    }
}

package com.cotani.location.storage;

import com.cotani.location.api.Home;
import com.cotani.location.api.HomeId;
import com.cotani.location.api.LocationName;
import com.cotani.location.api.LocationPosition;
import com.cotani.location.api.LocationRepository;
import com.cotani.location.api.LocationSnapshot;
import com.cotani.location.api.Warp;
import com.cotani.location.api.WarpId;
import com.cotani.storage.api.CotaniStorage;
import com.cotani.storage.migration.Migration;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** SQL-backed repository with atomic per-location upserts and deletes. */
public final class StorageLocationRepository implements LocationRepository {
    private static final String TABLE = "cotani_locations";
    private final CotaniStorage storage;

    public StorageLocationRepository(CotaniStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
    }

    @Override
    public CompletionStage<LocationSnapshot> loadAsync() {
        return storage.queryExecutor()
                .queryMany(
                        "SELECT location_type, owner_id, name, world_id, x, y, z, yaw, pitch, created_at, updated_at FROM "
                                + TABLE
                                + " ORDER BY location_type ASC, owner_id ASC, name ASC",
                        _ -> {},
                        StorageLocationRepository::mapRow)
                .thenApply(StorageLocationRepository::toSnapshot);
    }

    @Override
    public CompletionStage<Void> saveHomeAsync(Home home) {
        Objects.requireNonNull(home, "home");
        var position = home.position();
        return storage.table(TABLE)
                .upsert()
                .value("location_id", homeId(home.id()))
                .value("location_type", "HOME")
                .value("owner_id", home.id().ownerId())
                .value("name", home.id().name().value())
                .value("world_id", position.worldId())
                .value("x", position.x())
                .value("y", position.y())
                .value("z", position.z())
                .value("yaw", (double) position.yaw())
                .value("pitch", (double) position.pitch())
                .value("created_at", home.createdAt())
                .value("updated_at", home.updatedAt())
                .conflict("location_id")
                .update(
                        "location_type",
                        "owner_id",
                        "name",
                        "world_id",
                        "x",
                        "y",
                        "z",
                        "yaw",
                        "pitch",
                        "created_at",
                        "updated_at")
                .execute();
    }

    @Override
    public CompletionStage<Void> deleteHomeAsync(HomeId id) {
        Objects.requireNonNull(id, "id");
        return storage.table(TABLE).delete().where("location_id", homeId(id)).execute();
    }

    @Override
    public CompletionStage<Void> saveWarpAsync(Warp warp) {
        Objects.requireNonNull(warp, "warp");
        var position = warp.position();
        return storage.table(TABLE)
                .upsert()
                .value("location_id", warpId(warp.id()))
                .value("location_type", "WARP")
                .value("owner_id", null)
                .value("name", warp.id().name().value())
                .value("world_id", position.worldId())
                .value("x", position.x())
                .value("y", position.y())
                .value("z", position.z())
                .value("yaw", (double) position.yaw())
                .value("pitch", (double) position.pitch())
                .value("created_at", warp.createdAt())
                .value("updated_at", warp.updatedAt())
                .conflict("location_id")
                .update(
                        "location_type",
                        "owner_id",
                        "name",
                        "world_id",
                        "x",
                        "y",
                        "z",
                        "yaw",
                        "pitch",
                        "created_at",
                        "updated_at")
                .execute();
    }

    @Override
    public CompletionStage<Void> deleteWarpAsync(WarpId id) {
        Objects.requireNonNull(id, "id");
        return storage.table(TABLE).delete().where("location_id", warpId(id)).execute();
    }

    public static List<Migration> migrations() {
        return List.of(new CreateLocationTablesMigration(), new CreateLocationIndexesMigration());
    }

    private static SavedRow mapRow(com.cotani.storage.query.Row row) throws SQLException {
        var type = row.getString("location_type");
        var ownerId = row.getUuidOptional("owner_id");
        var position = new LocationPosition(
                row.getUuidOptional("world_id").orElseThrow(),
                row.getDouble("x"),
                row.getDouble("y"),
                row.getDouble("z"),
                (float) row.getDouble("yaw"),
                (float) row.getDouble("pitch"));
        var name = LocationName.of(row.getString("name"));
        var createdAt = row.getInstantOptional("created_at").orElseThrow();
        var updatedAt = row.getInstantOptional("updated_at").orElseThrow();
        return new SavedRow(type, ownerId, name, position, createdAt, updatedAt);
    }

    private static LocationSnapshot toSnapshot(List<SavedRow> rows) {
        var homes = new ArrayList<Home>();
        var warps = new ArrayList<Warp>();
        rows.forEach(row -> {
            switch (row.type()) {
                case "HOME" ->
                    homes.add(new Home(
                            new HomeId(row.ownerId().orElseThrow(), row.name()),
                            row.position(),
                            row.createdAt(),
                            row.updatedAt()));
                case "WARP" ->
                    warps.add(new Warp(new WarpId(row.name()), row.position(), row.createdAt(), row.updatedAt()));
                default -> throw new IllegalStateException("Unknown location type: " + row.type());
            }
        });
        return new LocationSnapshot(homes, warps);
    }

    private static String homeId(HomeId id) {
        return "home:" + id.ownerId() + ":" + id.name().value();
    }

    private static String warpId(WarpId id) {
        return "warp:" + id.name().value();
    }

    private record SavedRow(
            String type,
            java.util.Optional<UUID> ownerId,
            LocationName name,
            LocationPosition position,
            java.time.Instant createdAt,
            java.time.Instant updatedAt) {}
}

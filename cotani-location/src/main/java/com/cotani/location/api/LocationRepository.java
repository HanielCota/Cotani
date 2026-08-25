package com.cotani.location.api;

import java.util.concurrent.CompletionStage;

/**
 * Persistence SPI for saved locations.
 *
 * <p>Implementations must be asynchronous, must not access Bukkit objects, and must make each mutation atomic
 * for its location key. The service serializes calls made through one {@link LocationService}; implementations
 * may safely use their own executor or storage scheduler.
 */
public interface LocationRepository {
    /** Loads the complete persisted state before a service becomes available. */
    CompletionStage<LocationSnapshot> loadAsync();

    /** Inserts or replaces one player home. */
    CompletionStage<Void> saveHomeAsync(Home home);

    /** Deletes one player home. */
    CompletionStage<Void> deleteHomeAsync(HomeId id);

    /** Inserts or replaces one global warp. */
    CompletionStage<Void> saveWarpAsync(Warp warp);

    /** Deletes one global warp. */
    CompletionStage<Void> deleteWarpAsync(WarpId id);
}

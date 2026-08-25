package com.cotani.location;

import com.cotani.location.api.LocationRepository;
import com.cotani.location.api.LocationService;
import com.cotani.location.api.LocationServiceOptions;
import com.cotani.location.api.LocationTeleportService;
import com.cotani.location.internal.DefaultLocationService;
import com.cotani.location.internal.DefaultLocationTeleportService;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.TeleportService;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Factories for the {@code cotani-location} module. */
public final class CotaniLocations {
    private CotaniLocations() {}

    /** Creates an isolated in-memory location service. */
    public static LocationService inMemory() {
        return inMemory(LocationServiceOptions.defaults());
    }

    /** Creates an isolated in-memory service with explicit operational options. */
    public static LocationService inMemory(LocationServiceOptions options) {
        Objects.requireNonNull(options, "options");
        return DefaultLocationService.inMemory(options, Clock.systemUTC());
    }

    /** Restores a location service from a repository asynchronously; loading must complete before use. */
    public static CompletionStage<LocationService> fromRepositoryAsync(LocationRepository repository) {
        return fromRepositoryAsync(repository, LocationServiceOptions.defaults());
    }

    /** Restores a location service with explicit operational options. */
    public static CompletionStage<LocationService> fromRepositoryAsync(
            LocationRepository repository, LocationServiceOptions options) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        try {
            var loadStage = Objects.requireNonNull(repository.loadAsync(), "repository load stage");
            return options.withRepositoryTimeout(loadStage)
                    .thenApply(snapshot ->
                            DefaultLocationService.create(snapshot, repository, options, Clock.systemUTC()));
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    /** Creates the Bukkit/Folia bridge used to teleport to saved locations. */
    public static LocationTeleportService teleports(
            LocationService locations, TeleportService teleportService, PaperTaskScheduler scheduler) {
        return new DefaultLocationTeleportService(locations, teleportService, scheduler);
    }
}

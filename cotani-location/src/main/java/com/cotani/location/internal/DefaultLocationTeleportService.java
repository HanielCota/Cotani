package com.cotani.location.internal;

import com.cotani.api.InternalApi;
import com.cotani.location.api.HomeNotFoundException;
import com.cotani.location.api.LocationName;
import com.cotani.location.api.LocationPosition;
import com.cotani.location.api.LocationService;
import com.cotani.location.api.LocationTeleportService;
import com.cotani.location.api.WarpNotFoundException;
import com.cotani.location.api.WorldUnavailableException;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import com.cotani.teleport.api.TeleportRequest;
import com.cotani.teleport.api.TeleportResult;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

@InternalApi
public final class DefaultLocationTeleportService implements LocationTeleportService {
    private final LocationService locations;
    private final com.cotani.teleport.api.TeleportService teleportService;
    private final PaperTaskScheduler scheduler;

    public DefaultLocationTeleportService(
            LocationService locations,
            com.cotani.teleport.api.TeleportService teleportService,
            PaperTaskScheduler scheduler) {
        this.locations = Objects.requireNonNull(locations, "locations");
        this.teleportService = Objects.requireNonNull(teleportService, "teleportService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletionStage<TeleportResult> teleportHomeAsync(
            UUID playerId, LocationName name, TeleportOptions options) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        return locations.findHomeAsync(playerId, name).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new HomeNotFoundException(new com.cotani.location.api.HomeId(playerId, name)));
            }
            return teleport(playerId, found.orElseThrow().position(), TeleportCause.HOME, options);
        });
    }

    @Override
    public CompletionStage<TeleportResult> teleportWarpAsync(
            UUID playerId, LocationName name, TeleportOptions options) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(options, "options");
        return locations.findWarpAsync(name).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.failedFuture(
                        new WarpNotFoundException(new com.cotani.location.api.WarpId(name)));
            }
            return teleport(playerId, found.orElseThrow().position(), TeleportCause.WARP, options);
        });
    }

    private CompletionStage<TeleportResult> teleport(
            UUID playerId, LocationPosition position, TeleportCause cause, TeleportOptions options) {
        return scheduler
                .supply(ExecutionTarget.global(), "location-resolve", () -> resolve(position))
                .thenCompose(target -> teleportService.teleportAsync(TeleportRequest.builder()
                        .playerId(playerId)
                        .target(target)
                        .cause(cause)
                        .options(options)
                        .source("cotani-location")
                        .build()));
    }

    private static Location resolve(LocationPosition position) {
        World world = Bukkit.getWorld(position.worldId());
        if (world == null) {
            throw new WorldUnavailableException(position.worldId());
        }
        return new Location(world, position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }
}

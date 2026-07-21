package com.cotani.teleport.safety;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Default safe-location resolver.
 *
 * <p>The search reuses a single mutable {@link Location} while scanning candidates to avoid allocating
 * one object per iteration. World-border, height-bounds and chunk-loaded checks that do not change
 * during the search are validated once before the loop.
 *
 * <p>To respect Folia region affinity, the horizontal search is clamped to the chunk of the target
 * location. Callers that need a wider search must schedule multiple region tasks.
 */
public final class DefaultSafeLocationResolver implements com.cotani.teleport.safety.SafeLocationResolver {

    private final PaperTaskScheduler scheduler;

    public DefaultSafeLocationResolver(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private static Optional<Location> resolveSync(Location target, SafeLocationOptions options) {
        World world = target.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        if (BlockSafetyChecker.isSafe(target, options)) {
            return Optional.of(BlockSafetyChecker.center(target));
        }

        int baseX = target.getBlockX();
        int baseY = target.getBlockY();
        int baseZ = target.getBlockZ();
        int horizontal = Math.max(0, options.horizontalRadius());
        int vertical = Math.max(0, options.verticalRadius());

        int chunkMinX = (baseX >> 4) << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = (baseZ >> 4) << 4;
        int chunkMaxZ = chunkMinZ + 15;

        Location candidate = new Location(world, 0, 0, 0, target.getYaw(), target.getPitch());

        Optional<Location> up = search(
                world,
                candidate,
                baseX,
                baseY,
                baseZ,
                horizontal,
                vertical,
                1,
                chunkMinX,
                chunkMaxX,
                chunkMinZ,
                chunkMaxZ,
                options);
        if (up.isPresent()) {
            return up;
        }
        return search(
                world,
                candidate,
                baseX,
                baseY,
                baseZ,
                horizontal,
                vertical,
                -1,
                chunkMinX,
                chunkMaxX,
                chunkMinZ,
                chunkMaxZ,
                options);
    }

    private static Optional<Location> search(
            World world,
            Location candidate,
            int baseX,
            int baseY,
            int baseZ,
            int horizontal,
            int vertical,
            int direction,
            int chunkMinX,
            int chunkMaxX,
            int chunkMinZ,
            int chunkMaxZ,
            SafeLocationOptions options) {
        int startOffset = direction > 0 ? 0 : 1;

        for (int yOffset = startOffset; yOffset <= vertical; yOffset++) {
            int y = baseY + (yOffset * direction);
            if (y < world.getMinHeight() || y + 1 >= world.getMaxHeight()) {
                continue;
            }
            int minX = Math.max(chunkMinX, baseX - horizontal);
            int maxX = Math.min(chunkMaxX, baseX + horizontal);
            int minZ = Math.max(chunkMinZ, baseZ - horizontal);
            int maxZ = Math.min(chunkMaxZ, baseZ + horizontal);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    candidate.setX(x + 0.5);
                    candidate.setY(y);
                    candidate.setZ(z + 0.5);
                    if (BlockSafetyChecker.isSafe(candidate, options)) {
                        return Optional.of(candidate.clone());
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public CompletableFuture<Optional<Location>> resolve(Location target, SafeLocationOptions options) {
        Location cloned = target.clone();
        World world = cloned.getWorld();
        if (world == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        int chunkX = cloned.getBlockX() >> 4;
        int chunkZ = cloned.getBlockZ() >> 4;

        return world.getChunkAtAsync(chunkX, chunkZ)
                .thenCompose(_ -> scheduler.supply(
                        ExecutionTarget.region(cloned), "safe-location-resolve", () -> resolveSync(cloned, options)));
    }
}

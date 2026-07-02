package com.cotani.teleport.safety;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;

public final class DefaultSafeLocationResolver implements com.cotani.teleport.safety.SafeLocationResolver {

    private final PaperTaskScheduler scheduler;

    public DefaultSafeLocationResolver(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private record SearchArea(int baseX, int baseY, int baseZ, int horizontal, int vertical, int direction) {}

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

        var up = new SearchArea(baseX, baseY, baseZ, horizontal, vertical, 1);
        var down = new SearchArea(baseX, baseY, baseZ, horizontal, vertical, -1);
        return search(world, target, options, up).or(() -> search(world, target, options, down));
    }

    private static Optional<Location> search(
            World world, Location target, SafeLocationOptions options, SearchArea area) {
        Location candidate = new Location(world, 0, 0, 0);
        candidate.setYaw(target.getYaw());
        candidate.setPitch(target.getPitch());

        for (int yOffset = area.direction() > 0 ? 0 : 1; yOffset <= area.vertical(); yOffset++) {
            int y = area.baseY() + (yOffset * area.direction());
            for (int xOffset = -area.horizontal(); xOffset <= area.horizontal(); xOffset++) {
                for (int zOffset = -area.horizontal(); zOffset <= area.horizontal(); zOffset++) {
                    candidate.setX(area.baseX() + xOffset + 0.5);
                    candidate.setY(y);
                    candidate.setZ(area.baseZ() + zOffset + 0.5);
                    if (BlockSafetyChecker.isSafe(candidate, options)) {
                        return Optional.of(candidate.clone());
                    }
                }
            }
        }
        return Optional.empty();
    }
}

package com.cotani.teleport.safety;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.SafeLocationOptions;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.World;

public final class DefaultSafeLocationResolver implements SafeLocationResolver {

    private final PaperTaskScheduler scheduler;

    public DefaultSafeLocationResolver(PaperTaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public CompletableFuture<Optional<Location>> resolve(Location target, SafeLocationOptions options) {
        Location cloned = target.clone();
        return scheduler.supply(ExecutionTarget.global(), "safe-location-resolve", () -> resolveSync(cloned, options));
    }

    private Optional<Location> resolveSync(Location target, SafeLocationOptions options) {
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

        return search(world, target, options, baseX, baseY, baseZ, horizontal, vertical, 1)
                .or(() -> search(world, target, options, baseX, baseY, baseZ, horizontal, vertical, -1));
    }

    private Optional<Location> search(
            World world,
            Location target,
            SafeLocationOptions options,
            int baseX,
            int baseY,
            int baseZ,
            int horizontal,
            int vertical,
            int direction) {
        Location candidate = new Location(world, 0, 0, 0);
        candidate.setYaw(target.getYaw());
        candidate.setPitch(target.getPitch());

        for (int yOffset = direction > 0 ? 0 : 1; yOffset <= vertical; yOffset++) {
            int y = baseY + (yOffset * direction);
            for (int xOffset = -horizontal; xOffset <= horizontal; xOffset++) {
                for (int zOffset = -horizontal; zOffset <= horizontal; zOffset++) {
                    candidate.setX(baseX + xOffset + 0.5);
                    candidate.setY(y);
                    candidate.setZ(baseZ + zOffset + 0.5);
                    if (BlockSafetyChecker.isSafe(candidate, options)) {
                        return Optional.of(candidate.clone());
                    }
                }
            }
        }
        return Optional.empty();
    }
}

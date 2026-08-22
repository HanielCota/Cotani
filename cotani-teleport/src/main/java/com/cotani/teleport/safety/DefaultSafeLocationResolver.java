package com.cotani.teleport.safety;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.teleport.api.SafeLocationOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Default safe-location resolver.
 *
 * <p>Candidate offsets are evaluated in ascending 3D Euclidean distance order so that the returned
 * safe location is guaranteed to be the nearest safe location to the requested target.
 *
 * <p>To respect Folia region affinity, horizontal search candidate coordinates are clamped to the chunk
 * of the target location.
 */
public final class DefaultSafeLocationResolver implements SafeLocationResolver {
    private static final Map<Long, List<Offset>> OFFSET_CACHE = new ConcurrentHashMap<>();

    private final PaperTaskScheduler scheduler;

    public DefaultSafeLocationResolver(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    private record Offset(int dx, int dy, int dz, int distSq) implements Comparable<Offset> {
        @Override
        public int compareTo(Offset o) {
            return Integer.compare(this.distSq, o.distSq);
        }
    }

    private static List<Offset> getOrComputeOffsets(int horizontal, int vertical) {
        // Candidates are clamped to the target chunk (~16x16), so radii beyond that only waste memory.
        int effectiveHorizontal = Math.clamp(horizontal, 0, 15);
        int effectiveVertical = Math.clamp(vertical, 0, 64);
        long key = (((long) effectiveHorizontal) << 32) | (effectiveVertical & 0xFFFFFFFFL);

        return OFFSET_CACHE.computeIfAbsent(key, _ -> {
            List<Offset> offsets = new ArrayList<>();

            for (int dy = -effectiveVertical; dy <= effectiveVertical; dy++) {
                for (int dx = -effectiveHorizontal; dx <= effectiveHorizontal; dx++) {
                    for (int dz = -effectiveHorizontal; dz <= effectiveHorizontal; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        int distSq = dx * dx + dy * dy + dz * dz;
                        offsets.add(new Offset(dx, dy, dz, distSq));
                    }
                }
            }
            Collections.sort(offsets);

            return List.copyOf(offsets);
        });
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

        int minWorldY = world.getMinHeight() + 1;
        int maxWorldY = world.getMaxHeight() - 1;

        Location candidate = new Location(world, 0, 0, 0, target.getYaw(), target.getPitch());

        List<Offset> offsets = getOrComputeOffsets(horizontal, vertical);

        for (Offset offset : offsets) {
            int x = baseX + offset.dx();
            int z = baseZ + offset.dz();
            int y = baseY + offset.dy();

            if (y < minWorldY || y >= maxWorldY) {
                continue;
            }

            if ((x >> 4) != (baseX >> 4) || (z >> 4) != (baseZ >> 4)) {
                continue;
            }

            candidate.setX(x + 0.5);
            candidate.setY(y);
            candidate.setZ(z + 0.5);

            if (BlockSafetyChecker.isSafe(candidate, options)) {
                return Optional.of(candidate.clone());
            }
        }

        return Optional.empty();
    }

    @Override
    public CompletionStage<Optional<Location>> resolve(Location target, SafeLocationOptions options) {
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

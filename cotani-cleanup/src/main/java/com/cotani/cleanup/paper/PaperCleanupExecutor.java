package com.cotani.cleanup.paper;

import com.cotani.cleanup.api.CleanupEntitySnapshot;
import com.cotani.cleanup.api.CleanupExecutor;
import com.cotani.cleanup.api.CleanupFailure;
import com.cotani.cleanup.api.CleanupPolicy;
import com.cotani.cleanup.api.CleanupProtection;
import com.cotani.cleanup.api.CleanupRemovalResult;
import com.cotani.cleanup.api.CleanupScan;
import com.cotani.cleanup.api.CleanupTarget;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;
import org.jspecify.annotations.Nullable;

/** Paper/Folia adapter that scans loaded chunks and removes entities on their owning entity threads. */
public final class PaperCleanupExecutor implements CleanupExecutor {
    private static final Logger LOGGER = Logger.getLogger(PaperCleanupExecutor.class.getName());

    private final PaperTaskScheduler scheduler;
    private final CleanupProtection protection;

    public PaperCleanupExecutor(PaperTaskScheduler scheduler) {
        this(scheduler, CleanupProtection.none());
    }

    public PaperCleanupExecutor(PaperTaskScheduler scheduler, CleanupProtection protection) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.protection = Objects.requireNonNull(protection, "protection");
    }

    @Override
    public CompletionStage<CleanupScan> scanAsync(CleanupPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        return scheduler
                .supply(ExecutionTarget.global(), "cleanup-list-loaded-chunks", () -> loadedChunks(policy))
                .thenCompose(chunks -> scanChunks(policy, chunks, 0, new ScanAccumulator()));
    }

    @Override
    public CompletionStage<CleanupRemovalResult> removeAsync(
            CleanupPolicy policy, List<CleanupEntitySnapshot> candidates) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(candidates, "candidates");
        return removeEntities(policy, candidates, 0, CleanupRemovalResult.empty());
    }

    private List<ChunkCoordinate> loadedChunks(CleanupPolicy policy) {
        var chunks = new ArrayList<ChunkCoordinate>();
        for (var world : Bukkit.getWorlds()) {
            if (!policy.worldIds().isEmpty() && !policy.worldIds().contains(world.getUID())) {
                continue;
            }
            for (var chunk : world.getLoadedChunks()) {
                chunks.add(new ChunkCoordinate(world.getUID(), chunk.getX(), chunk.getZ()));
            }
        }
        return List.copyOf(chunks);
    }

    private CompletionStage<CleanupScan> scanChunks(
            CleanupPolicy policy, List<ChunkCoordinate> chunks, int index, ScanAccumulator accumulated) {
        if (index >= chunks.size()) {
            return CompletableFuture.completedFuture(accumulated.toScan(policy));
        }
        var coordinate = chunks.get(index);
        var regionScan = scheduler.supply(
                ExecutionTarget.region(coordinate.worldId(), coordinate.chunkX(), coordinate.chunkZ()),
                "cleanup-scan-region",
                () -> scanChunk(policy, coordinate));
        return regionScan.thenCompose(result -> {
            accumulated.add(result, policy.maxEntities());
            return scanChunks(policy, chunks, index + 1, accumulated);
        });
    }

    private RegionScan scanChunk(CleanupPolicy policy, ChunkCoordinate coordinate) {
        var world = Bukkit.getWorld(coordinate.worldId());
        if (world == null || !world.isChunkLoaded(coordinate.chunkX(), coordinate.chunkZ())) {
            return RegionScan.empty();
        }
        var chunk = world.getChunkAt(coordinate.chunkX(), coordinate.chunkZ(), false);
        long scanned = 0;
        long matched = 0;
        var candidates = new ArrayList<CleanupEntitySnapshot>();
        var matchedByTarget = new HashMap<CleanupTarget, Long>();
        for (var entity : chunk.getEntities()) {
            scanned++;
            var snapshot = snapshot(entity);
            if (snapshot == null || !isEligible(policy, snapshot)) {
                continue;
            }
            matched++;
            matchedByTarget.merge(snapshot.target(), 1L, Long::sum);
            if (candidates.size() < policy.maxEntities()) {
                candidates.add(snapshot);
            }
        }
        return new RegionScan(scanned, matched, candidates, matchedByTarget);
    }

    private CompletionStage<CleanupRemovalResult> removeEntities(
            CleanupPolicy policy, List<CleanupEntitySnapshot> candidates, int index, CleanupRemovalResult accumulated) {
        if (index >= candidates.size()) {
            return CompletableFuture.completedFuture(accumulated);
        }
        var candidate = candidates.get(index);
        var current = scheduler.supply(
                ExecutionTarget.entity(candidate.entityId()),
                "cleanup-remove-entity",
                () -> removeOnEntityThread(policy, candidate));
        return current.handle((result, failure) -> {
                    if (failure == null) {
                        return result;
                    }
                    LOGGER.log(Level.WARNING, "Cleanup entity task failed. entityId=" + candidate.entityId(), failure);
                    return failedRemoval(candidate, failure);
                })
                .thenCompose(result -> removeEntities(policy, candidates, index + 1, merge(accumulated, result)));
    }

    private CleanupRemovalResult removeOnEntityThread(CleanupPolicy policy, CleanupEntitySnapshot candidate) {
        var entity = Bukkit.getEntity(candidate.entityId());
        if (entity == null) {
            return skippedRemoval();
        }
        try {
            var current = snapshot(entity);
            if (current == null || !isEligible(policy, current)) {
                return skippedRemoval();
            }
            entity.remove();
            if (entity.isValid()) {
                return failedRemoval(
                        current, new IllegalStateException("Entity remained valid after remove() was requested"));
            }
            return removedRemoval(current.target());
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Cleanup entity removal failed. entityId=" + candidate.entityId(), failure);
            return failedRemoval(candidate, failure);
        }
    }

    private boolean isEligible(CleanupPolicy policy, CleanupEntitySnapshot entity) {
        return policy.matches(entity) && !protection.isProtected(entity);
    }

    private static CleanupRemovalResult merge(CleanupRemovalResult first, CleanupRemovalResult second) {
        var removedByTarget = new HashMap<CleanupTarget, Long>(first.removedByTarget());
        second.removedByTarget().forEach((target, amount) -> removedByTarget.merge(target, amount, Long::sum));
        var failures = new ArrayList<CleanupFailure>(first.failures());
        failures.addAll(second.failures());
        return new CleanupRemovalResult(
                first.removed() + second.removed(),
                first.skipped() + second.skipped(),
                first.failed() + second.failed(),
                failures,
                removedByTarget);
    }

    private static CleanupRemovalResult removedRemoval(CleanupTarget target) {
        return new CleanupRemovalResult(1, 0, 0, List.of(), Map.of(target, 1L));
    }

    private static CleanupRemovalResult skippedRemoval() {
        return new CleanupRemovalResult(0, 1, 0, List.of(), Map.of());
    }

    private static CleanupRemovalResult failedRemoval(CleanupEntitySnapshot candidate, Throwable failure) {
        var message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        var detail = new CleanupFailure(
                candidate.entityId(),
                candidate.worldId(),
                candidate.target(),
                message.substring(0, Math.min(512, message.length())));
        return new CleanupRemovalResult(0, 0, 1, List.of(detail), Map.of());
    }

    private static @Nullable CleanupEntitySnapshot snapshot(Entity entity) {
        var target = targetOf(entity.getType());
        if (target == null) {
            return null;
        }
        var location = entity.getLocation();
        var world = entity.getWorld();
        var ageTicks = Math.max(0L, entity.getTicksLived());
        return new CleanupEntitySnapshot(
                entity.getUniqueId(),
                world.getUID(),
                location.getChunk().getX(),
                location.getChunk().getZ(),
                target,
                Duration.ofMillis(ageTicks * 50L),
                entity.customName() != null,
                entity.isPersistent(),
                entity instanceof Tameable tameable && tameable.isTamed(),
                entity.getScoreboardTags());
    }

    private static @Nullable CleanupTarget targetOf(EntityType type) {
        return switch (type) {
            case ITEM -> CleanupTarget.DROPPED_ITEM;
            case EXPERIENCE_ORB -> CleanupTarget.EXPERIENCE_ORB;
            case ARROW, SPECTRAL_ARROW -> CleanupTarget.ARROW;
            case TRIDENT -> CleanupTarget.TRIDENT;
            case SNOWBALL -> CleanupTarget.SNOWBALL;
            case EGG -> CleanupTarget.EGG;
            case ENDER_PEARL -> CleanupTarget.ENDER_PEARL;
            case SPLASH_POTION -> CleanupTarget.SPLASH_POTION;
            case LINGERING_POTION -> CleanupTarget.LINGERING_POTION;
            case FIREBALL -> CleanupTarget.FIREBALL;
            case SMALL_FIREBALL -> CleanupTarget.SMALL_FIREBALL;
            case DRAGON_FIREBALL -> CleanupTarget.DRAGON_FIREBALL;
            case WITHER_SKULL -> CleanupTarget.WITHER_SKULL;
            case SHULKER_BULLET -> CleanupTarget.SHULKER_BULLET;
            case FISHING_BOBBER -> CleanupTarget.FISHING_HOOK;
            case MINECART,
                    CHEST_MINECART,
                    FURNACE_MINECART,
                    HOPPER_MINECART,
                    TNT_MINECART,
                    COMMAND_BLOCK_MINECART,
                    SPAWNER_MINECART -> CleanupTarget.MINECART;
            default -> type.name().endsWith("BOAT") ? CleanupTarget.BOAT : null;
        };
    }

    private record ChunkCoordinate(UUID worldId, int chunkX, int chunkZ) {}

    private record RegionScan(
            long scanned,
            long matched,
            List<CleanupEntitySnapshot> candidates,
            Map<CleanupTarget, Long> matchedByTarget) {
        private static RegionScan empty() {
            return new RegionScan(0, 0, List.of(), Map.of());
        }
    }

    private static final class ScanAccumulator {
        private long scanned;
        private long matched;
        private final List<CleanupEntitySnapshot> candidates = new ArrayList<>();
        private final Map<CleanupTarget, Long> matchedByTarget = new HashMap<>();

        private void add(RegionScan region, int maxEntities) {
            scanned += region.scanned();
            matched += region.matched();
            var remaining = maxEntities - candidates.size();
            if (remaining > 0) {
                candidates.addAll(region.candidates()
                        .subList(0, Math.min(remaining, region.candidates().size())));
            }
            region.matchedByTarget().forEach((target, amount) -> matchedByTarget.merge(target, amount, Long::sum));
        }

        private CleanupScan toScan(CleanupPolicy policy) {
            var limitedCandidates = candidates.size() <= policy.maxEntities()
                    ? candidates
                    : candidates.subList(0, policy.maxEntities());
            return new CleanupScan(scanned, matched, limitedCandidates, matchedByTarget);
        }
    }
}

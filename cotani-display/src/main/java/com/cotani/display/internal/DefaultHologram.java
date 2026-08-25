package com.cotani.display.internal;

import com.cotani.api.InternalApi;
import com.cotani.display.api.Hologram;
import com.cotani.display.api.HologramClickHandler;
import com.cotani.display.api.HologramLine;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultHologram implements Hologram {

    private static final String LINE_REQUIRED = "line cannot be null";

    private final UUID id;
    private final @Nullable String name;
    private final List<HologramLine> lines = new CopyOnWriteArrayList<>();
    private final double lineSpacing;
    private final boolean clickable;
    private final AtomicReference<@Nullable HologramClickHandler> clickHandler = new AtomicReference<>();
    private final PaperTaskScheduler scheduler;
    private final @Nullable DefaultHologramService service;
    private final DisplayEntityRenderer renderer = new DisplayEntityRenderer();

    private final AtomicReference<@Nullable Location> locationRef = new AtomicReference<>();
    private final List<UUID> lineEntityIds = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable UUID> interactionId = new AtomicReference<>();
    private final AtomicBoolean spawned = new AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong mutationGeneration =
            new java.util.concurrent.atomic.AtomicLong();

    public DefaultHologram(
            UUID id,
            @Nullable String name,
            List<HologramLine> initialLines,
            double lineSpacing,
            boolean clickable,
            @Nullable HologramClickHandler handler,
            PaperTaskScheduler scheduler) {
        this(id, name, initialLines, lineSpacing, clickable, handler, scheduler, null);
    }

    public DefaultHologram(
            UUID id,
            @Nullable String name,
            List<HologramLine> initialLines,
            double lineSpacing,
            boolean clickable,
            @Nullable HologramClickHandler handler,
            PaperTaskScheduler scheduler,
            @Nullable DefaultHologramService service) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = name;
        for (HologramLine initialLine : Objects.requireNonNull(initialLines, "initialLines cannot be null")) {
            this.lines.add(Objects.requireNonNull(initialLine, "initialLine cannot be null"));
        }
        this.lineSpacing = lineSpacing;
        this.clickable = clickable;
        this.clickHandler.set(handler);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.service = service;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    @Override
    public Location location() {
        var loc = locationRef.get();
        if (loc == null) {
            throw new IllegalStateException("Hologram has not been spawned at any location yet");
        }
        return loc.clone();
    }

    @Override
    public List<HologramLine> lines() {
        return List.copyOf(lines);
    }

    @Override
    public boolean isSpawned() {
        return spawned.get();
    }

    @Override
    public List<UUID> entityIds() {
        return List.copyOf(allSpawnedEntityIds());
    }

    @Override
    public Optional<UUID> interactionEntityId() {
        return Optional.ofNullable(interactionId.get());
    }

    @Override
    public Optional<HologramClickHandler> clickHandler() {
        return Optional.ofNullable(clickHandler.get());
    }

    @Override
    public void setClickHandler(@Nullable HologramClickHandler handler) {
        this.clickHandler.set(handler);
    }

    @Override
    public CompletionStage<Hologram> spawnAsync(Location spawnLocation) {
        Objects.requireNonNull(spawnLocation, "spawnLocation cannot be null");
        var target = spawnLocation.clone();
        locationRef.set(target);
        long generation = mutationGeneration.incrementAndGet();

        return CompletableFuture.supplyAsync(
                () -> {
                    if (mutationGeneration.get() != generation) {
                        return this;
                    }
                    spawnSync(target);
                    spawned.set(true);
                    return this;
                },
                scheduler.regionExecutor(target));
    }

    @Override
    public CompletionStage<Void> teleportAsync(Location newLocation) {
        Objects.requireNonNull(newLocation, "newLocation cannot be null");
        var target = newLocation.clone();
        var oldLoc = locationRef.get();
        long generation = mutationGeneration.incrementAndGet();

        if (!spawned.get() || oldLoc == null || oldLoc.getWorld() == null) {
            locationRef.set(target);
            return CompletableFuture.completedFuture(null);
        }

        boolean sameRegion = Objects.equals(oldLoc.getWorld(), target.getWorld())
                && (oldLoc.getBlockX() >> 4) == (target.getBlockX() >> 4)
                && (oldLoc.getBlockZ() >> 4) == (target.getBlockZ() >> 4);

        if (sameRegion) {
            return CompletableFuture.runAsync(
                    () -> {
                        if (mutationGeneration.get() != generation) {
                            return;
                        }
                        despawnSync();
                        spawnSync(target);
                    },
                    scheduler.regionExecutor(target));
        }

        return CompletableFuture.runAsync(
                        () -> {
                            if (mutationGeneration.get() != generation) {
                                return;
                            }
                            despawnSync();
                        },
                        scheduler.regionExecutor(oldLoc))
                .thenRunAsync(
                        () -> {
                            if (mutationGeneration.get() != generation) {
                                return;
                            }
                            spawnSync(target);
                        },
                        scheduler.regionExecutor(target));
    }

    @Override
    public CompletionStage<Void> updateLineAsync(int index, HologramLine line) {
        Objects.requireNonNull(line, LINE_REQUIRED);
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("Line index " + index + " out of bounds for size " + lines.size());
        }

        lines.set(index, line);

        var loc = locationRef.get();
        if (!spawned.get() || loc == null || loc.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        long generation = mutationGeneration.incrementAndGet();
        return CompletableFuture.runAsync(
                () -> {
                    if (mutationGeneration.get() != generation || !spawned.get()) {
                        return;
                    }
                    if (index < lineEntityIds.size()) {
                        var currentEntityId = lineEntityIds.get(index);
                        var lineLoc = calculateLineLocation(loc, index);
                        var updatedEntityId = renderer.updateLine(currentEntityId, line, lineLoc);
                        if (!updatedEntityId.equals(currentEntityId)) {
                            lineEntityIds.set(index, updatedEntityId);
                            if (service != null) {
                                service.unbindEntity(currentEntityId);
                                service.bindEntity(updatedEntityId, this);
                            }
                        }
                    }
                },
                scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> addLineAsync(HologramLine line) {
        Objects.requireNonNull(line, LINE_REQUIRED);
        lines.add(line);

        var loc = locationRef.get();
        if (!spawned.get() || loc == null || loc.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        long generation = mutationGeneration.incrementAndGet();
        return CompletableFuture.runAsync(
                () -> {
                    if (mutationGeneration.get() != generation || !spawned.get()) {
                        return;
                    }
                    despawnSync();
                    spawnSync(loc);
                },
                scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> removeLineAsync(int index) {
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("Line index " + index + " out of bounds for size " + lines.size());
        }

        lines.remove(index);

        var loc = locationRef.get();
        if (!spawned.get() || loc == null || loc.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        long generation = mutationGeneration.incrementAndGet();
        return CompletableFuture.runAsync(
                () -> {
                    if (mutationGeneration.get() != generation || !spawned.get()) {
                        return;
                    }
                    despawnSync();
                    spawnSync(loc);
                },
                scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> clearLinesAsync() {
        lines.clear();

        var loc = locationRef.get();
        if (!spawned.get() || loc == null || loc.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        long generation = mutationGeneration.incrementAndGet();
        return CompletableFuture.runAsync(
                () -> {
                    if (mutationGeneration.get() != generation) {
                        return;
                    }
                    despawnSync();
                },
                scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> destroyAsync() {
        long generation = mutationGeneration.incrementAndGet();
        var loc = locationRef.get();
        spawned.set(false);
        if (loc == null || loc.getWorld() == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                () -> {
                    if (mutationGeneration.get() != generation) {
                        return;
                    }
                    despawnSync();
                },
                scheduler.regionExecutor(loc));
    }

    private Location calculateLineLocation(Location baseLocation, int targetIndex) {
        double currentY = baseLocation.getY();
        for (int i = 0; i < targetIndex; i++) {
            var l = lines.get(i);
            double spacing = l.heightOffset() > 0 ? l.heightOffset() : lineSpacing;
            currentY -= spacing;
        }
        return new Location(
                baseLocation.getWorld(),
                baseLocation.getX(),
                currentY,
                baseLocation.getZ(),
                baseLocation.getYaw(),
                baseLocation.getPitch());
    }

    private void spawnSync(Location baseLocation) {
        despawnSync();
        locationRef.set(baseLocation.clone());
        var result = renderer.spawn(baseLocation, lines, lineSpacing, clickable);
        lineEntityIds.addAll(result.lineEntityIds());
        for (UUID uuid : result.allEntityIds()) {
            if (service != null) {
                service.bindEntity(uuid, this);
            }
        }
        interactionId.set(result.interactionEntityId());
    }

    private void despawnSync() {
        var toDespawn = allSpawnedEntityIds();
        if (service != null) {
            for (UUID entityId : toDespawn) {
                service.unbindEntity(entityId);
            }
        }
        renderer.despawn(toDespawn);
        lineEntityIds.clear();
        interactionId.set(null);
    }

    private List<UUID> allSpawnedEntityIds() {
        var all = new java.util.ArrayList<>(lineEntityIds);
        var interact = interactionId.get();
        if (interact != null) {
            all.add(interact);
        }
        return all;
    }
}

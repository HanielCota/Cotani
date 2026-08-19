package com.cotani.display.impl;

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
    private final List<UUID> entityIds = new CopyOnWriteArrayList<>();
    private final AtomicReference<@Nullable UUID> interactionId = new AtomicReference<>();
    private final AtomicBoolean spawned = new AtomicBoolean();

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
        this.lines.addAll(Objects.requireNonNull(initialLines, "initialLines cannot be null"));
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
        return List.copyOf(entityIds);
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

        return CompletableFuture.supplyAsync(
                () -> {
                    spawnSync(target);
                    return (Hologram) this;
                },
                scheduler.regionExecutor(target));
    }

    @Override
    public CompletionStage<Void> teleportAsync(Location newLocation) {
        Objects.requireNonNull(newLocation, "newLocation cannot be null");
        var target = newLocation.clone();
        locationRef.set(target);

        if (!spawned.get()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                () -> {
                    despawnSync();
                    spawnSync(target);
                },
                scheduler.regionExecutor(target));
    }

    @Override
    public CompletionStage<Void> updateLineAsync(int index, HologramLine line) {
        Objects.requireNonNull(line, "line cannot be null");
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("Line index " + index + " out of bounds for size " + lines.size());
        }

        lines.set(index, line);

        var loc = locationRef.get();
        if (!spawned.get() || loc == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                () -> {
                    if (index < entityIds.size()) {
                        renderer.updateLine(entityIds.get(index), line);
                    }
                },
                scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> addLineAsync(HologramLine line) {
        Objects.requireNonNull(line, "line cannot be null");
        lines.add(line);

        var loc = locationRef.get();
        if (!spawned.get() || loc == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                () -> {
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
        if (!spawned.get() || loc == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                () -> {
                    despawnSync();
                    spawnSync(loc);
                },
                scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> clearLinesAsync() {
        lines.clear();

        var loc = locationRef.get();
        if (!spawned.get() || loc == null) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(this::despawnSync, scheduler.regionExecutor(loc));
    }

    @Override
    public CompletionStage<Void> destroyAsync() {
        var loc = locationRef.get();
        if (!spawned.get() || loc == null) {
            spawned.set(false);
            despawnSync();
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(
                () -> {
                    despawnSync();
                    spawned.set(false);
                },
                scheduler.regionExecutor(loc));
    }

    private void spawnSync(Location baseLocation) {
        despawnSync();
        var result = renderer.spawn(baseLocation, lines, lineSpacing, clickable);
        for (UUID uuid : result.allEntityIds()) {
            entityIds.add(uuid);
            if (service != null) {
                service.bindEntity(uuid, this);
            }
        }
        interactionId.set(result.interactionEntityId());
        spawned.set(!entityIds.isEmpty());
    }

    private void despawnSync() {
        if (service != null) {
            for (UUID entityId : entityIds) {
                service.unbindEntity(entityId);
            }
        }
        renderer.despawn(entityIds);
        entityIds.clear();
        interactionId.set(null);
    }
}

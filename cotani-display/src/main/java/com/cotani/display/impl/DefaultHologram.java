package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.BlockLine;
import com.cotani.display.api.Hologram;
import com.cotani.display.api.HologramClickHandler;
import com.cotani.display.api.HologramLine;
import com.cotani.display.api.ItemLine;
import com.cotani.display.api.TextLine;
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
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
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
                        var entityId = entityIds.get(index);
                        var entity = Bukkit.getEntity(entityId);
                        if (entity != null) {
                            applyLineProperties(entity, line);
                        }
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
        World world = baseLocation.getWorld();
        if (world == null) {
            return;
        }

        despawnSync();
        double currentY = baseLocation.getY();

        // Spawn lines from top to bottom
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var lineLocation = new Location(
                    world,
                    baseLocation.getX(),
                    currentY,
                    baseLocation.getZ(),
                    baseLocation.getYaw(),
                    baseLocation.getPitch());

            Entity entity = spawnLineEntity(world, lineLocation, line);
            if (entity != null) {
                var uuid = entity.getUniqueId();
                entityIds.add(uuid);
                if (service != null) {
                    service.bindEntity(uuid, this);
                }
            }

            double spacing = line.heightOffset() > 0 ? line.heightOffset() : lineSpacing;
            currentY -= spacing;
        }

        if (clickable && !lines.isEmpty()) {
            double totalHeight = Math.max(0.5, (baseLocation.getY() - currentY) + 0.2);
            var interactionLocation = new Location(
                    world,
                    baseLocation.getX(),
                    currentY,
                    baseLocation.getZ(),
                    baseLocation.getYaw(),
                    baseLocation.getPitch());
            var interaction = (Interaction) world.spawnEntity(interactionLocation, EntityType.INTERACTION);
            interaction.setPersistent(false);
            interaction.setInteractionWidth(1.2f);
            interaction.setInteractionHeight((float) totalHeight);
            interaction.setResponsive(true);
            var interactionUuid = interaction.getUniqueId();
            interactionId.set(interactionUuid);
            entityIds.add(interactionUuid);
            if (service != null) {
                service.bindEntity(interactionUuid, this);
            }
        }

        spawned.set(true);
    }

    private Entity spawnLineEntity(World world, Location location, HologramLine line) {
        return switch (line) {
            case TextLine textLine -> {
                var textDisplay = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
                textDisplay.setPersistent(false);
                textDisplay.text(textLine.text());
                textDisplay.setBillboard(textLine.billboard().toBukkit());
                textDisplay.setShadowed(textLine.shadow());
                textDisplay.setSeeThrough(textLine.seeThrough());
                textDisplay.setViewRange(textLine.viewRange());
                if (textLine.backgroundColor() != null) {
                    textDisplay.setBackgroundColor(textLine.backgroundColor());
                }
                if (textLine.scale() != 1.0f) {
                    textDisplay.setTransformation(scaleTransformation(textLine.scale()));
                }
                yield textDisplay;
            }
            case ItemLine itemLine -> {
                var itemDisplay = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
                itemDisplay.setPersistent(false);
                itemDisplay.setItemStack(itemLine.item());
                itemDisplay.setItemDisplayTransform(itemLine.itemTransform());
                itemDisplay.setBillboard(itemLine.billboard().toBukkit());
                itemDisplay.setViewRange(itemLine.viewRange());
                if (itemLine.scale() != 1.0f) {
                    itemDisplay.setTransformation(scaleTransformation(itemLine.scale()));
                }
                yield itemDisplay;
            }
            case BlockLine blockLine -> {
                var blockDisplay = (BlockDisplay) world.spawnEntity(location, EntityType.BLOCK_DISPLAY);
                blockDisplay.setPersistent(false);
                blockDisplay.setBlock(blockLine.blockData());
                blockDisplay.setBillboard(blockLine.billboard().toBukkit());
                blockDisplay.setViewRange(blockLine.viewRange());
                if (blockLine.scale() != 1.0f) {
                    blockDisplay.setTransformation(scaleTransformation(blockLine.scale()));
                }
                yield blockDisplay;
            }
        };
    }

    private void applyLineProperties(Entity entity, HologramLine line) {
        switch (line) {
            case TextLine textLine
            when entity instanceof TextDisplay textDisplay -> {
                textDisplay.text(textLine.text());
                textDisplay.setBillboard(textLine.billboard().toBukkit());
                textDisplay.setShadowed(textLine.shadow());
                textDisplay.setSeeThrough(textLine.seeThrough());
                textDisplay.setViewRange(textLine.viewRange());
                if (textLine.backgroundColor() != null) {
                    textDisplay.setBackgroundColor(textLine.backgroundColor());
                }
                if (textLine.scale() != 1.0f) {
                    textDisplay.setTransformation(scaleTransformation(textLine.scale()));
                }
            }
            case ItemLine itemLine
            when entity instanceof ItemDisplay itemDisplay -> {
                itemDisplay.setItemStack(itemLine.item());
                itemDisplay.setItemDisplayTransform(itemLine.itemTransform());
                itemDisplay.setBillboard(itemLine.billboard().toBukkit());
                itemDisplay.setViewRange(itemLine.viewRange());
                if (itemLine.scale() != 1.0f) {
                    itemDisplay.setTransformation(scaleTransformation(itemLine.scale()));
                }
            }
            case BlockLine blockLine
            when entity instanceof BlockDisplay blockDisplay -> {
                blockDisplay.setBlock(blockLine.blockData());
                blockDisplay.setBillboard(blockLine.billboard().toBukkit());
                blockDisplay.setViewRange(blockLine.viewRange());
                if (blockLine.scale() != 1.0f) {
                    blockDisplay.setTransformation(scaleTransformation(blockLine.scale()));
                }
            }
            default -> {}
        }
    }

    private static Transformation scaleTransformation(float scale) {
        return new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }

    private void despawnSync() {
        for (UUID entityId : entityIds) {
            if (service != null) {
                service.unbindEntity(entityId);
            }
            var entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        entityIds.clear();
        interactionId.set(null);
    }
}

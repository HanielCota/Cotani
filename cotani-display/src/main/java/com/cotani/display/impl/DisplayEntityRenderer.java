package com.cotani.display.impl;

import com.cotani.api.InternalApi;
import com.cotani.display.api.BlockLine;
import com.cotani.display.api.HologramLine;
import com.cotani.display.api.ItemLine;
import com.cotani.display.api.TextLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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

/**
 * Dedicated internal renderer responsible for spawning, updating and despawning Bukkit Display entities.
 */
@InternalApi
public final class DisplayEntityRenderer {

    public record RenderedEntities(
            List<UUID> lineEntityIds, @Nullable UUID interactionEntityId) {
        public RenderedEntities {
            lineEntityIds = List.copyOf(Objects.requireNonNull(lineEntityIds, "lineEntityIds cannot be null"));
        }

        public List<UUID> allEntityIds() {
            if (interactionEntityId == null) {
                return lineEntityIds;
            }
            var all = new ArrayList<>(lineEntityIds);
            all.add(interactionEntityId);
            return List.copyOf(all);
        }
    }

    /**
     * Spawns all display entities and optional interaction hitbox for a hologram.
     * Must be invoked on the region thread of the location.
     *
     * @param baseLocation the base spawn location
     * @param lines the hologram lines to render
     * @param lineSpacing default spacing between lines
     * @param clickable whether to spawn an interaction hitbox
     * @return the rendered entity UUIDs
     */
    public RenderedEntities spawn(
            Location baseLocation, List<HologramLine> lines, double lineSpacing, boolean clickable) {
        Objects.requireNonNull(baseLocation, "baseLocation cannot be null");
        Objects.requireNonNull(lines, "lines cannot be null");
        World world = baseLocation.getWorld();
        if (world == null) {
            return new RenderedEntities(List.of(), null);
        }

        List<UUID> spawnedIds = new ArrayList<>();
        UUID interactionId = null;
        double currentY = baseLocation.getY();

        try {
            for (HologramLine line : lines) {
                Objects.requireNonNull(line, "line cannot be null");
                var lineLocation = new Location(
                        world,
                        baseLocation.getX(),
                        currentY,
                        baseLocation.getZ(),
                        baseLocation.getYaw(),
                        baseLocation.getPitch());

                Entity entity = spawnLineEntity(world, lineLocation, line);
                spawnedIds.add(entity.getUniqueId());

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
                interactionId = interaction.getUniqueId();
            }

            return new RenderedEntities(spawnedIds, interactionId);
        } catch (RuntimeException failure) {
            despawn(spawnedIds);
            if (interactionId != null) {
                despawn(List.of(interactionId));
            }
            throw failure;
        }
    }

    /**
     * Updates an existing spawned line entity with new properties, replacing it if incompatible.
     * Must be invoked on the region thread.
     *
     * @param entityId the entity UUID
     * @param line the line definition
     * @param lineLocation the location of the line if respawning is required
     * @return the resulting entity UUID (either the same or newly spawned)
     */
    public UUID updateLine(UUID entityId, HologramLine line, Location lineLocation) {
        Objects.requireNonNull(entityId, "entityId cannot be null");
        Objects.requireNonNull(line, "line cannot be null");
        Objects.requireNonNull(lineLocation, "lineLocation cannot be null");

        Entity entity = null;
        try {
            entity = Bukkit.getEntity(entityId);
        } catch (Exception _) {
            // Uninitialized server or invalid lookup
        }

        if (entity != null && entity.isValid() && isEntityCompatible(entity, line)) {
            applyLineProperties(entity, line);
            return entityId;
        }

        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        World world = lineLocation.getWorld();
        if (world == null) {
            return entityId;
        }
        return spawnLineEntity(world, lineLocation, line).getUniqueId();
    }

    /**
     * Despawns all entities in the list.
     * Must be invoked on the region thread.
     *
     * @param entityIds the entity UUIDs to remove
     */
    public void despawn(Iterable<UUID> entityIds) {
        Objects.requireNonNull(entityIds, "entityIds cannot be null");
        for (UUID entityId : entityIds) {
            try {
                var entity = Bukkit.getEntity(entityId);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
            } catch (Exception _) {
                // Uninitialized server or entity already removed
            }
        }
    }

    public Entity spawnLineEntity(World world, Location location, HologramLine line) {
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(location, "location cannot be null");
        Objects.requireNonNull(line, "line cannot be null");

        return switch (line) {
            case TextLine textLine -> {
                var textDisplay = (TextDisplay) world.spawnEntity(location, EntityType.TEXT_DISPLAY);
                textDisplay.setPersistent(false);
                textDisplay.text(textLine.text());
                textDisplay.setBillboard(textLine.billboard().toBukkit());
                textDisplay.setShadowed(textLine.shadow());
                textDisplay.setSeeThrough(textLine.seeThrough());
                textDisplay.setTextOpacity(textLine.textOpacity());
                textDisplay.setViewRange(textLine.viewRange());
                textDisplay.setBackgroundColor(textLine.backgroundColor());
                textDisplay.setTransformation(scaleTransformation(textLine.scale()));
                yield textDisplay;
            }
            case ItemLine itemLine -> {
                var itemDisplay = (ItemDisplay) world.spawnEntity(location, EntityType.ITEM_DISPLAY);
                itemDisplay.setPersistent(false);
                itemDisplay.setItemStack(itemLine.item());
                itemDisplay.setItemDisplayTransform(itemLine.itemTransform());
                itemDisplay.setBillboard(itemLine.billboard().toBukkit());
                itemDisplay.setViewRange(itemLine.viewRange());
                itemDisplay.setTransformation(scaleTransformation(itemLine.scale()));
                yield itemDisplay;
            }
            case BlockLine blockLine -> {
                var blockDisplay = (BlockDisplay) world.spawnEntity(location, EntityType.BLOCK_DISPLAY);
                blockDisplay.setPersistent(false);
                blockDisplay.setBlock(blockLine.blockData());
                blockDisplay.setBillboard(blockLine.billboard().toBukkit());
                blockDisplay.setViewRange(blockLine.viewRange());
                blockDisplay.setTransformation(scaleTransformation(blockLine.scale()));
                yield blockDisplay;
            }
        };
    }

    public void applyLineProperties(Entity entity, HologramLine line) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(line, "line cannot be null");

        switch (line) {
            case TextLine textLine
            when entity instanceof TextDisplay textDisplay -> {
                textDisplay.text(textLine.text());
                textDisplay.setBillboard(textLine.billboard().toBukkit());
                textDisplay.setShadowed(textLine.shadow());
                textDisplay.setSeeThrough(textLine.seeThrough());
                textDisplay.setTextOpacity(textLine.textOpacity());
                textDisplay.setViewRange(textLine.viewRange());
                textDisplay.setBackgroundColor(textLine.backgroundColor());
                textDisplay.setTransformation(scaleTransformation(textLine.scale()));
            }
            case ItemLine itemLine
            when entity instanceof ItemDisplay itemDisplay -> {
                itemDisplay.setItemStack(itemLine.item());
                itemDisplay.setItemDisplayTransform(itemLine.itemTransform());
                itemDisplay.setBillboard(itemLine.billboard().toBukkit());
                itemDisplay.setViewRange(itemLine.viewRange());
                itemDisplay.setTransformation(scaleTransformation(itemLine.scale()));
            }
            case BlockLine blockLine
            when entity instanceof BlockDisplay blockDisplay -> {
                blockDisplay.setBlock(blockLine.blockData());
                blockDisplay.setBillboard(blockLine.billboard().toBukkit());
                blockDisplay.setViewRange(blockLine.viewRange());
                blockDisplay.setTransformation(scaleTransformation(blockLine.scale()));
            }
            default -> {
                // Line type does not match the live entity; the caller respawns it.
            }
        }
    }

    public boolean isEntityCompatible(Entity entity, HologramLine line) {
        Objects.requireNonNull(entity, "entity cannot be null");
        Objects.requireNonNull(line, "line cannot be null");

        return switch (line) {
            case TextLine _ -> entity instanceof TextDisplay;
            case ItemLine _ -> entity instanceof ItemDisplay;
            case BlockLine _ -> entity instanceof BlockDisplay;
        };
    }

    public static Transformation scaleTransformation(float scale) {
        return new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }
}

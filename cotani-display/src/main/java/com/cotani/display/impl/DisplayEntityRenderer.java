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
        World world = baseLocation.getWorld();
        if (world == null) {
            return new RenderedEntities(List.of(), null);
        }

        List<UUID> spawnedIds = new ArrayList<>();
        double currentY = baseLocation.getY();

        for (HologramLine line : lines) {
            var lineLocation = new Location(
                    world,
                    baseLocation.getX(),
                    currentY,
                    baseLocation.getZ(),
                    baseLocation.getYaw(),
                    baseLocation.getPitch());

            Entity entity = spawnLineEntity(world, lineLocation, line);
            if (entity != null) {
                spawnedIds.add(entity.getUniqueId());
            }

            double spacing = line.heightOffset() > 0 ? line.heightOffset() : lineSpacing;
            currentY -= spacing;
        }

        UUID interactionId = null;
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
    }

    /**
     * Updates an existing spawned line entity with new properties.
     * Must be invoked on the region thread.
     *
     * @param entityId the entity UUID
     * @param line the line definition
     */
    public void updateLine(UUID entityId, HologramLine line) {
        var entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            applyLineProperties(entity, line);
        }
    }

    /**
     * Despawns all entities in the list.
     * Must be invoked on the region thread.
     *
     * @param entityIds the entity UUIDs to remove
     */
    public void despawn(Iterable<UUID> entityIds) {
        for (UUID entityId : entityIds) {
            var entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
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

    public static Transformation scaleTransformation(float scale) {
        return new Transformation(
                new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }
}

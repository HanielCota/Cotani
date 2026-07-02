package com.cotani.task.api;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jspecify.annotations.Nullable;

public sealed interface ExecutionTarget
        permits ExecutionTarget.Async, ExecutionTarget.Global, ExecutionTarget.Region, ExecutionTarget.EntityTarget {

    Async ASYNC = new Async();
    Global GLOBAL = new Global();

    static ExecutionTarget async() {
        return ASYNC;
    }

    static ExecutionTarget global() {
        return GLOBAL;
    }

    static ExecutionTarget region(Location location) {
        Objects.requireNonNull(location, "location");
        var world = Objects.requireNonNull(location.getWorld(), "location.world");

        return new Region(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4, location);
    }

    static ExecutionTarget region(UUID worldId, int chunkX, int chunkZ) {
        return new Region(worldId, chunkX, chunkZ, null);
    }

    static ExecutionTarget entity(Entity entity) {
        Objects.requireNonNull(entity, "entity");

        return new EntityTarget(entity.getUniqueId(), entity);
    }

    static ExecutionTarget entity(UUID entityId) {
        return new EntityTarget(entityId, null);
    }

    record Async() implements ExecutionTarget {}

    record Global() implements ExecutionTarget {}

    record Region(
            UUID worldId, int chunkX, int chunkZ, @Nullable Location location) implements ExecutionTarget {
        public Region {
            Objects.requireNonNull(worldId, "worldId");
        }

        @Override
        public Location location() {
            if (location != null) {
                return location;
            }
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                throw new IllegalStateException("World not found: " + worldId);
            }
            return new Location(world, chunkX << 4, 0, chunkZ << 4);
        }
    }

    record EntityTarget(UUID entityId, @Nullable Entity entity) implements ExecutionTarget {
        public EntityTarget {
            Objects.requireNonNull(entityId, "entityId");
        }

        @Override
        public Entity entity() {
            if (entity != null) {
                return entity;
            }
            Entity resolved = Bukkit.getEntity(entityId);
            if (resolved == null) {
                throw new IllegalStateException("Entity not found: " + entityId);
            }
            return resolved;
        }
    }
}

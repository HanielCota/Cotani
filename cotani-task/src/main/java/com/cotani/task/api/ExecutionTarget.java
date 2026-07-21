package com.cotani.task.api;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Identifies the thread/region/entity on which a task should run.
 *
 * <p>Instances are immutable identifiers (world UUID + chunk coordinates or entity UUID). Live Bukkit
 * objects such as {@link Location} and {@link Entity} are never captured into async flows; they are
 * resolved only when the task is about to run on the correct thread.
 */
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

        return new Region(world.getUID(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    static ExecutionTarget region(UUID worldId, int chunkX, int chunkZ) {
        return new Region(worldId, chunkX, chunkZ);
    }

    static ExecutionTarget entity(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        return new EntityTarget(entity.getUniqueId());
    }

    static ExecutionTarget entity(UUID entityId) {
        return new EntityTarget(entityId);
    }

    record Async() implements ExecutionTarget {}

    record Global() implements ExecutionTarget {}

    record Region(UUID worldId, int chunkX, int chunkZ) implements ExecutionTarget {
        public Region {
            Objects.requireNonNull(worldId, "worldId");
        }

        /**
         * Resolves this region to a {@link Location} using {@link Bukkit#getWorld(UUID)}.
         *
         * <p>This must only be called from the server main thread. Do not call it from async
         * continuations or task bodies.
         */
        public Location location() {
            World world = Bukkit.getWorld(worldId);
            if (world == null) {
                throw new IllegalStateException("World not found: " + worldId);
            }
            return new Location(world, chunkX << 4, 0, chunkZ << 4);
        }
    }

    record EntityTarget(UUID entityId) implements ExecutionTarget {
        public EntityTarget {
            Objects.requireNonNull(entityId, "entityId");
        }

        /**
         * Resolves this target to a live {@link Entity} using {@link Bukkit#getEntity(UUID)}.
         *
         * <p>This must only be called from the server main thread or the entity's own thread. Do not
         * call it from async continuations or task bodies.
         */
        public Entity entity() {
            Entity resolved = Bukkit.getEntity(entityId);
            if (resolved == null) {
                throw new IllegalStateException("Entity not found: " + entityId);
            }
            return resolved;
        }
    }
}

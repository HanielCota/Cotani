package com.cotani.cleanup.api;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable entity information captured on the owning Paper world thread. */
public record CleanupEntitySnapshot(
        UUID entityId,
        UUID worldId,
        int chunkX,
        int chunkZ,
        CleanupTarget target,
        Duration age,
        boolean named,
        boolean persistent,
        boolean tamed,
        Set<String> tags) {
    public CleanupEntitySnapshot(
            UUID entityId,
            UUID worldId,
            int chunkX,
            int chunkZ,
            CleanupTarget target,
            Duration age,
            boolean named,
            boolean persistent,
            boolean tamed) {
        this(entityId, worldId, chunkX, chunkZ, target, age, named, persistent, tamed, Set.of());
    }

    public CleanupEntitySnapshot {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(age, "age");
        Objects.requireNonNull(tags, "tags");
        tags = Set.copyOf(tags);
        if (age.isNegative()) {
            throw new IllegalArgumentException("age must not be negative");
        }
    }
}

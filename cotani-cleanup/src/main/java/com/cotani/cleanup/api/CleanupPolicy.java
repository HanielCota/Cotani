package com.cotani.cleanup.api;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable allow-list policy that decides which captured entities may be removed. */
public record CleanupPolicy(
        Set<CleanupTarget> targets,
        Set<UUID> worldIds,
        Set<UUID> excludedEntityIds,
        Duration minimumAge,
        boolean protectNamed,
        boolean protectPersistent,
        boolean protectTamed,
        int maxEntities,
        Set<String> protectedTags) {
    public CleanupPolicy(
            Set<CleanupTarget> targets,
            Set<UUID> worldIds,
            Set<UUID> excludedEntityIds,
            Duration minimumAge,
            boolean protectNamed,
            boolean protectPersistent,
            boolean protectTamed,
            int maxEntities) {
        this(
                targets,
                worldIds,
                excludedEntityIds,
                minimumAge,
                protectNamed,
                protectPersistent,
                protectTamed,
                maxEntities,
                Set.of());
    }

    public CleanupPolicy {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(worldIds, "worldIds");
        Objects.requireNonNull(excludedEntityIds, "excludedEntityIds");
        Objects.requireNonNull(minimumAge, "minimumAge");
        Objects.requireNonNull(protectedTags, "protectedTags");
        targets = Set.copyOf(targets);
        worldIds = Set.copyOf(worldIds);
        excludedEntityIds = Set.copyOf(excludedEntityIds);
        protectedTags = Set.copyOf(protectedTags);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }
        if (minimumAge.isNegative()) {
            throw new IllegalArgumentException("minimumAge must not be negative");
        }
        if (protectedTags.stream().anyMatch(tag -> tag.isBlank())) {
            throw new IllegalArgumentException("protectedTags must not contain blank values");
        }
        if (maxEntities <= 0 || maxEntities > 1_000_000) {
            throw new IllegalArgumentException("maxEntities must be between 1 and 1000000");
        }
    }

    /** Returns a conservative policy for ordinary dropped-item and XP-orb cleanup. */
    public static CleanupPolicy defaults() {
        return builder().build();
    }

    public boolean matches(CleanupEntitySnapshot entity) {
        Objects.requireNonNull(entity, "entity");
        return targets.contains(entity.target())
                && (worldIds.isEmpty() || worldIds.contains(entity.worldId()))
                && !excludedEntityIds.contains(entity.entityId())
                && entity.age().compareTo(minimumAge) >= 0
                && (!protectNamed || !entity.named())
                && (!protectPersistent || !entity.persistent())
                && (!protectTamed || !entity.tamed())
                && entity.tags().stream().noneMatch(protectedTags::contains);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder that keeps cleanup targets explicit. */
    public static final class Builder {
        private final Set<CleanupTarget> targets = EnumSet.of(CleanupTarget.DROPPED_ITEM, CleanupTarget.EXPERIENCE_ORB);
        private final Set<UUID> worldIds = new java.util.HashSet<>();
        private final Set<UUID> excludedEntityIds = new java.util.HashSet<>();
        private Duration minimumAge = Duration.ofMinutes(5);
        private boolean protectNamed = true;
        private boolean protectPersistent = true;
        private boolean protectTamed = true;
        private int maxEntities = 10_000;
        private final Set<String> protectedTags = new java.util.HashSet<>();

        public Builder targets(Collection<CleanupTarget> values) {
            Objects.requireNonNull(values, "values");
            targets.clear();
            targets.addAll(values);
            return this;
        }

        public Builder target(CleanupTarget target) {
            targets.add(Objects.requireNonNull(target, "target"));
            return this;
        }

        public Builder worlds(Collection<UUID> values) {
            Objects.requireNonNull(values, "values");
            worldIds.clear();
            values.forEach(value -> worldIds.add(Objects.requireNonNull(value, "worldId")));
            return this;
        }

        public Builder excludeEntities(Collection<UUID> values) {
            Objects.requireNonNull(values, "values");
            excludedEntityIds.clear();
            values.forEach(value -> excludedEntityIds.add(Objects.requireNonNull(value, "entityId")));
            return this;
        }

        public Builder minimumAge(Duration value) {
            minimumAge = Objects.requireNonNull(value, "minimumAge");
            return this;
        }

        public Builder protectNamed(boolean value) {
            protectNamed = value;
            return this;
        }

        public Builder protectPersistent(boolean value) {
            protectPersistent = value;
            return this;
        }

        public Builder protectTamed(boolean value) {
            protectTamed = value;
            return this;
        }

        public Builder maxEntities(int value) {
            maxEntities = value;
            return this;
        }

        public Builder protectedTags(Collection<String> values) {
            Objects.requireNonNull(values, "values");
            protectedTags.clear();
            values.forEach(value -> {
                Objects.requireNonNull(value, "protectedTag");
                if (value.isBlank()) {
                    throw new IllegalArgumentException("protectedTag must not be blank");
                }
                protectedTags.add(value);
            });
            return this;
        }

        public CleanupPolicy build() {
            return new CleanupPolicy(
                    targets,
                    worldIds,
                    excludedEntityIds,
                    minimumAge,
                    protectNamed,
                    protectPersistent,
                    protectTamed,
                    maxEntities,
                    protectedTags);
        }
    }
}

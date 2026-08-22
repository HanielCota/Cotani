package com.cotani.region.api;

import com.cotani.text.MiniMessages;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

/**
 * Immutable 3D spatial region with bounding box coordinates, priority, and protection flags.
 *
 * @param id unique region identifier
 * @param displayName user-facing display name
 * @param worldId world UUID
 * @param minX minimum X block coordinate
 * @param minY minimum Y block coordinate
 * @param minZ minimum Z block coordinate
 * @param maxX maximum X block coordinate
 * @param maxY maximum Y block coordinate
 * @param maxZ maximum Z block coordinate
 * @param priority precedence resolution priority (higher number takes precedence)
 * @param flags map of configured flags and boolean outcomes
 * @param greetingMessage optional message displayed to player on entry
 * @param farewellMessage optional message displayed to player on exit
 */
public record Region3D(
        String id,
        Component displayName,
        UUID worldId,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        int priority,
        Map<RegionFlag, Boolean> flags,
        @Nullable Component greetingMessage,
        @Nullable Component farewellMessage) {

    public Region3D {
        Objects.requireNonNull(id, "Parameter 'id' must not be null");
        Objects.requireNonNull(displayName, "Parameter 'displayName' must not be null");
        Objects.requireNonNull(worldId, "Parameter 'worldId' must not be null");
        Objects.requireNonNull(flags, "Parameter 'flags' must not be null");

        flags = Collections.unmodifiableMap(new EnumMap<>(flags));

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Minimum coordinates cannot exceed maximum coordinates");
        }
    }

    /**
     * Checks if a Bukkit location is inside this 3D region.
     *
     * @param location location to test
     * @return true if location is inside this region
     */
    public boolean contains(Location location) {
        Objects.requireNonNull(location, "Parameter 'location' must not be null");

        var world = location.getWorld();
        if (world == null || !worldId.equals(world.getUID())) {
            return false;
        }

        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Checks if block coordinates are inside this 3D region bounding box.
     *
     * @param x block X
     * @param y block Y
     * @param z block Z
     * @return true if inside bounding box
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /**
     * Calculates the total block volume of this region.
     *
     * @return volume in blocks
     */
    public long volume() {
        var dx = (long) (maxX - minX + 1);
        var dy = (long) (maxY - minY + 1);
        var dz = (long) (maxZ - minZ + 1);
        return dx * dy * dz;
    }

    /**
     * Queries a specific protection flag on this region.
     *
     * @param flag the flag to evaluate
     * @return Optional containing the boolean flag value, or empty if undefined
     */
    public Optional<Boolean> getFlag(RegionFlag flag) {
        Objects.requireNonNull(flag, "Parameter 'flag' must not be null");
        return Optional.ofNullable(flags.get(flag));
    }

    /**
     * Creates a new fluent {@link Builder}.
     *
     * @param id unique region identifier
     * @param worldId world UUID
     * @return a new Builder instance
     */
    public static Builder builder(String id, UUID worldId) {
        return new Builder(id, worldId);
    }

    /**
     * Fluent builder for {@link Region3D}.
     */
    public static final class Builder {
        private final String id;
        private final UUID worldId;
        private Component displayName;
        private int minX = 0;
        private int minY = -64;
        private int minZ = 0;
        private int maxX = 0;
        private int maxY = 320;
        private int maxZ = 0;
        private int priority = 0;
        private final Map<RegionFlag, Boolean> flags = new EnumMap<>(RegionFlag.class);
        private @Nullable Component greetingMessage;
        private @Nullable Component farewellMessage;

        public Builder(String id, UUID worldId) {
            this.id = Objects.requireNonNull(id, "id");
            this.worldId = Objects.requireNonNull(worldId, "worldId");
            this.displayName = Component.text(id);
        }

        public Builder name(Component displayName) {
            this.displayName = Objects.requireNonNull(displayName, "displayName");
            return this;
        }

        public Builder name(String miniMessageText) {
            Objects.requireNonNull(miniMessageText, "miniMessageText");
            this.displayName = MiniMessages.parse(miniMessageText);
            return this;
        }

        public Builder bounds(int x1, int y1, int z1, int x2, int y2, int z2) {
            this.minX = Math.min(x1, x2);
            this.minY = Math.min(y1, y2);
            this.minZ = Math.min(z1, z2);
            this.maxX = Math.max(x1, x2);
            this.maxY = Math.max(y1, y2);
            this.maxZ = Math.max(z1, z2);
            return this;
        }

        public Builder bounds(Location loc1, Location loc2) {
            Objects.requireNonNull(loc1, "loc1");
            Objects.requireNonNull(loc2, "loc2");
            return bounds(
                    loc1.getBlockX(),
                    loc1.getBlockY(),
                    loc1.getBlockZ(),
                    loc2.getBlockX(),
                    loc2.getBlockY(),
                    loc2.getBlockZ());
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder flag(RegionFlag flag, boolean allowed) {
            Objects.requireNonNull(flag, "flag");
            this.flags.put(flag, allowed);
            return this;
        }

        public Builder greeting(@Nullable Component message) {
            this.greetingMessage = message;
            return this;
        }

        public Builder greeting(String miniMessageText) {
            Objects.requireNonNull(miniMessageText, "miniMessageText");
            this.greetingMessage = MiniMessages.parse(miniMessageText);
            return this;
        }

        public Builder farewell(@Nullable Component message) {
            this.farewellMessage = message;
            return this;
        }

        public Builder farewell(String miniMessageText) {
            Objects.requireNonNull(miniMessageText, "miniMessageText");
            this.farewellMessage = MiniMessages.parse(miniMessageText);
            return this;
        }

        public Region3D build() {
            return new Region3D(
                    id,
                    displayName,
                    worldId,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    priority,
                    flags,
                    greetingMessage,
                    farewellMessage);
        }
    }
}

package com.cotani.location.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable, Bukkit-independent representation of a saved position. */
public record LocationPosition(UUID worldId, double x, double y, double z, float yaw, float pitch) {
    public LocationPosition {
        Objects.requireNonNull(worldId, "worldId");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}

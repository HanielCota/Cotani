package com.cotani.location.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a saved location points to a world that is not currently loaded. */
public final class WorldUnavailableException extends LocationException {
    private static final long serialVersionUID = 1L;
    private final UUID worldId;

    public WorldUnavailableException(UUID worldId) {
        super("World is not loaded: " + Objects.requireNonNull(worldId, "worldId"));
        this.worldId = worldId;
    }

    public UUID worldId() {
        return worldId;
    }
}

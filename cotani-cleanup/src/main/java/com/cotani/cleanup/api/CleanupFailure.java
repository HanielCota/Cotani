package com.cotani.cleanup.api;

import java.util.Objects;
import java.util.UUID;

/** Immutable description of an entity removal that failed. */
public record CleanupFailure(UUID entityId, UUID worldId, CleanupTarget target, String message) {
    public CleanupFailure {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(message, "message");
        if (message.isBlank() || message.length() > 512) {
            throw new IllegalArgumentException("message must contain between 1 and 512 characters");
        }
    }
}

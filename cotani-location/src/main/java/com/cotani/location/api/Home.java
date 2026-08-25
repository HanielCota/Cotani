package com.cotani.location.api;

import java.time.Instant;
import java.util.Objects;

/** Immutable player-owned home. */
public record Home(HomeId id, LocationPosition position, Instant createdAt, Instant updatedAt) {
    public Home {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}

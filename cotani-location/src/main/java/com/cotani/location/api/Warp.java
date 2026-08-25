package com.cotani.location.api;

import java.time.Instant;
import java.util.Objects;

/** Immutable global warp. */
public record Warp(WarpId id, LocationPosition position, Instant createdAt, Instant updatedAt) {
    public Warp {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}

package com.cotani.location.api;

import java.util.Objects;
import java.util.UUID;

/** Stable key for a player-owned home. */
public record HomeId(UUID ownerId, LocationName name) {
    public HomeId {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(name, "name");
    }

    public static HomeId of(UUID ownerId, String name) {
        return new HomeId(ownerId, LocationName.of(name));
    }
}

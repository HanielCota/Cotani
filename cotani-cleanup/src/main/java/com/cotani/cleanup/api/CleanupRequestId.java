package com.cotani.cleanup.api;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier for one cleanup request and its domain events. */
public record CleanupRequestId(UUID value) {
    public CleanupRequestId {
        Objects.requireNonNull(value, "value");
    }

    public static CleanupRequestId random() {
        return new CleanupRequestId(UUID.randomUUID());
    }
}

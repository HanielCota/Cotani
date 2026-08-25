package com.cotani.season.api;

import java.util.Objects;
import java.util.UUID;

/** Idempotency key for one experience grant. */
public record SeasonExperienceId(UUID value) {
    public SeasonExperienceId {
        Objects.requireNonNull(value, "value");
    }

    public static SeasonExperienceId random() {
        return new SeasonExperienceId(UUID.randomUUID());
    }
}

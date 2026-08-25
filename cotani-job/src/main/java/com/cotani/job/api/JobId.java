package com.cotani.job.api;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier shared by all attempts of one logical job. */
public record JobId(UUID value) {
    public JobId {
        Objects.requireNonNull(value, "value");
    }

    public static JobId random() {
        return new JobId(UUID.randomUUID());
    }
}

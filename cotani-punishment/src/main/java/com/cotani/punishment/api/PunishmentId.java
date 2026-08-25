package com.cotani.punishment.api;

import java.util.Objects;
import java.util.UUID;

/** Stable identifier used to make punishment writes idempotent. */
public record PunishmentId(UUID value) {
    public PunishmentId {
        Objects.requireNonNull(value, "value");
    }

    public static PunishmentId random() {
        return new PunishmentId(UUID.randomUUID());
    }
}

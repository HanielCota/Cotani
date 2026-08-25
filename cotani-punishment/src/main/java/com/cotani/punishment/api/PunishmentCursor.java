package com.cotani.punishment.api;

import java.time.Instant;
import java.util.Objects;

/** Stable cursor for descending punishment history pagination. */
public record PunishmentCursor(Instant createdAt, PunishmentId id) {
    public PunishmentCursor {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(id, "id");
    }

    public static PunishmentCursor from(Punishment punishment) {
        Objects.requireNonNull(punishment, "punishment");
        return new PunishmentCursor(punishment.createdAt(), punishment.id());
    }
}

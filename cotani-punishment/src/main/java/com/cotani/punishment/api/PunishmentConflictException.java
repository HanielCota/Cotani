package com.cotani.punishment.api;

/** Signals reuse of an idempotency ID with different punishment data. */
public final class PunishmentConflictException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    public PunishmentConflictException(PunishmentId id) {
        super("Punishment ID is already associated with different data: " + id.value());
    }
}

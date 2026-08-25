package com.cotani.location.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a player has reached the configured number of homes. */
public final class HomeLimitExceededException extends LocationException {
    private static final long serialVersionUID = 1L;
    private final UUID ownerId;
    private final int limit;

    public HomeLimitExceededException(UUID ownerId, int limit) {
        super("Player " + Objects.requireNonNull(ownerId, "ownerId") + " reached the home limit of " + limit);
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        this.ownerId = ownerId;
        this.limit = limit;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public int limit() {
        return limit;
    }
}

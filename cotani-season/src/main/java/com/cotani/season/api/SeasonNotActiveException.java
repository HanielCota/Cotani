package com.cotani.season.api;

import java.time.Instant;

/** Raised when experience is granted outside a season's active interval. */
public final class SeasonNotActiveException extends SeasonException {
    private static final long serialVersionUID = 1L;

    public SeasonNotActiveException(SeasonId seasonId, Instant occurredAt) {
        super("Season is not active at " + occurredAt + ": " + seasonId.value());
    }
}

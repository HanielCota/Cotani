package com.cotani.season.api;

/** Raised when a requested season is not registered. */
public final class SeasonNotFoundException extends SeasonException {
    private static final long serialVersionUID = 1L;

    public SeasonNotFoundException(SeasonId seasonId) {
        super("Season is not registered: " + seasonId.value());
    }
}

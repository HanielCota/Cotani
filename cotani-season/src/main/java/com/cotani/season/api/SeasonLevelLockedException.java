package com.cotani.season.api;

/** Raised when a player attempts to claim a level not yet unlocked. */
public final class SeasonLevelLockedException extends SeasonException {
    private static final long serialVersionUID = 1L;

    public SeasonLevelLockedException(SeasonId seasonId, int level) {
        super("Season level is not unlocked: " + seasonId.value() + "#" + level);
    }
}

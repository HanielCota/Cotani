package com.cotani.season.api;

/** Raised when one experience id is reused for a different player, season or amount. */
public final class SeasonExperienceConflictException extends SeasonException {
    private static final long serialVersionUID = 1L;

    public SeasonExperienceConflictException(SeasonExperienceId operationId) {
        super("Season experience operation was reused with different data: " + operationId.value());
    }
}

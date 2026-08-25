package com.cotani.season.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable idempotent request to add experience to a player's season progress. */
public record SeasonExperienceCommand(
        UUID playerId, SeasonId seasonId, long amount, SeasonExperienceId operationId, Instant occurredAt) {
    public SeasonExperienceCommand {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (amount <= 0 || amount > 1_000_000_000L) {
            throw new IllegalArgumentException("amount must be between 1 and 1000000000");
        }
    }
}

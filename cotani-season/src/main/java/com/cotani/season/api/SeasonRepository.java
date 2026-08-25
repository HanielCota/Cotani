package com.cotani.season.api;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Bukkit-free asynchronous persistence SPI for season progress and idempotent XP grants. */
public interface SeasonRepository {
    CompletionStage<Optional<SeasonProgress>> findAsync(UUID playerId, SeasonId seasonId);

    /** Applies one operation exactly once and returns the resulting durable progress. */
    CompletionStage<SeasonProgress> applyExperienceAsync(SeasonExperienceCommand command);

    /** Saves a claimed-level snapshot only when its stored revision matches the expected revision. */
    CompletionStage<SeasonProgress> saveAsync(SeasonProgress progress, long expectedRevision);

    /** Removes idempotency records older than the supplied cutoff. */
    CompletionStage<Void> purgeExperienceOperationsBeforeAsync(Instant cutoff);
}

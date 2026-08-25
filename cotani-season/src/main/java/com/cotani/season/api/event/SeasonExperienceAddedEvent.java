package com.cotani.season.api.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.season.api.SeasonExperienceId;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonProgress;
import java.util.Objects;
import java.util.UUID;

/** Published after an experience operation has been durably accepted. */
public record SeasonExperienceAddedEvent(
        UUID playerId, SeasonId seasonId, SeasonExperienceId operationId, long amount, SeasonProgress progress)
        implements CotaniEvent {
    public SeasonExperienceAddedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(progress, "progress");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (!progress.playerId().equals(playerId) || !progress.seasonId().equals(seasonId)) {
            throw new IllegalArgumentException("progress must belong to the event player and season");
        }
    }
}

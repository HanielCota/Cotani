package com.cotani.season.api;

import com.cotani.AsyncCloseable;
import com.cotani.reward.api.RewardClaim;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous seasonal progression with idempotent experience and reward claims. */
public interface SeasonService extends AsyncCloseable, AutoCloseable {
    void register(SeasonDefinition definition);

    Optional<SeasonDefinition> findDefinition(SeasonId seasonId);

    Optional<SeasonDefinition> findActiveDefinition(Instant instant);

    CompletionStage<Optional<SeasonProgress>> findProgressAsync(UUID playerId, SeasonId seasonId);

    default CompletionStage<SeasonProgress> addExperienceAsync(UUID playerId, SeasonId seasonId, long amount) {
        return addExperienceAsync(playerId, seasonId, amount, SeasonExperienceId.random());
    }

    CompletionStage<SeasonProgress> addExperienceAsync(
            UUID playerId, SeasonId seasonId, long amount, SeasonExperienceId operationId);

    /** Claims the level reward using a deterministic reward idempotency key. */
    CompletionStage<RewardClaim> claimLevelAsync(UUID playerId, SeasonId seasonId, int level);

    /** Purges old experience idempotency records according to the host retention policy. */
    CompletionStage<Void> purgeExperienceOperationsAsync(Instant cutoff);

    @Override
    CompletionStage<Void> closeAsync();

    @Override
    void close();
}

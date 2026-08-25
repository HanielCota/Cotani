package com.cotani.achievement.api;

import com.cotani.AsyncCloseable;
import com.cotani.reward.api.RewardClaim;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous achievement evaluation, unlock and idempotent reward claim use cases. */
public interface AchievementService extends AsyncCloseable, AutoCloseable {
    /**
     * Registers a process-local definition; duplicate ids are rejected.
     * Definitions must be registered again after a restart.
     */
    void register(AchievementDefinition definition);

    /** Returns a registered definition, if present. */
    Optional<AchievementDefinition> findDefinition(AchievementId achievementId);

    /**
     * Loads one player's progress after previously accepted mutations have settled.
     * Completes with {@link Optional#empty()} when no unlock has been persisted.
     */
    CompletionStage<Optional<AchievementProgress>> findProgressAsync(UUID playerId, AchievementId achievementId);

    /**
     * Evaluates every criterion and unlocks the achievement once all thresholds are satisfied.
     * The operation is idempotent and does not block the calling thread.
     */
    CompletionStage<AchievementProgress> evaluateAsync(UUID playerId, AchievementId achievementId);

    /**
     * Claims the configured reward using the stable claim id persisted at unlock time.
     * Repeating the call is safe when the configured reward service is idempotent.
     */
    CompletionStage<RewardClaim> claimRewardAsync(UUID playerId, AchievementId achievementId);

    /** Starts asynchronous shutdown and rejects new operations. */
    @Override
    void close();
}

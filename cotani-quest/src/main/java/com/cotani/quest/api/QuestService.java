package com.cotani.quest.api;

import com.cotani.AsyncCloseable;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Asynchronous objective progress and idempotent quest claim use cases. */
public interface QuestService extends AsyncCloseable, AutoCloseable {
    /** Registers a definition; duplicate ids are rejected. */
    void register(QuestDefinition definition);

    /** Returns a registered definition, if present. */
    Optional<QuestDefinition> findDefinition(QuestId questId);

    /** Loads one player's progress after previously accepted mutations become visible. */
    CompletionStage<Optional<QuestProgress>> findProgressAsync(UUID playerId, QuestId questId);

    /**
     * Adds bounded progress to an objective and completes it when every objective is satisfied.
     * The returned stage completes only after durable persistence; callers must not retry a failed
     * completion unless the failure is known to have happened before persistence.
     */
    CompletionStage<QuestProgress> recordProgressAsync(
            UUID playerId, QuestId questId, QuestObjectiveId objectiveId, long amount);

    /** Claims a completed quest with a generated idempotency key. */
    default CompletionStage<QuestClaim> claimAsync(UUID playerId, QuestId questId) {
        return claimAsync(playerId, questId, QuestClaimId.random());
    }

    /** Claims a completed quest using a caller-owned idempotency key. */
    CompletionStage<QuestClaim> claimAsync(UUID playerId, QuestId questId, QuestClaimId claimId);

    /** Starts asynchronous shutdown and rejects new mutations. */
    @Override
    void close();
}

package com.cotani.quest.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable per-player quest progress snapshot. */
public record QuestProgress(
        UUID playerId,
        QuestId questId,
        Map<QuestObjectiveId, Long> objectiveProgress,
        boolean completed,
        Optional<Instant> completedAt,
        Optional<QuestClaimId> claimId,
        Optional<Instant> claimedAt,
        long revision) {
    public QuestProgress {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(objectiveProgress, "objectiveProgress");
        Objects.requireNonNull(completedAt, "completedAt");
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(claimedAt, "claimedAt");
        objectiveProgress.forEach((objectiveId, amount) -> {
            Objects.requireNonNull(objectiveId, "objective id");
            Objects.requireNonNull(amount, "objective progress");
            if (amount < 0) {
                throw new IllegalArgumentException("objective progress cannot be negative");
            }
        });
        if (completed != completedAt.isPresent()) {
            throw new IllegalArgumentException("completed and completedAt must agree");
        }
        if (claimId.isPresent() != claimedAt.isPresent()) {
            throw new IllegalArgumentException("claimId and claimedAt must agree");
        }
        if (claimId.isPresent() && !completed) {
            throw new IllegalArgumentException("a quest can only be claimed after completion");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        objectiveProgress = Map.copyOf(objectiveProgress);
    }

    public static QuestProgress initial(UUID playerId, QuestId questId) {
        return new QuestProgress(
                playerId, questId, Map.of(), false, Optional.empty(), Optional.empty(), Optional.empty(), 0);
    }

    public long progressFor(QuestObjectiveId objectiveId) {
        Objects.requireNonNull(objectiveId, "objectiveId");
        return objectiveProgress.getOrDefault(objectiveId, 0L);
    }

    public boolean isClaimed() {
        return claimId.isPresent();
    }

    public QuestProgress withRevision(long newRevision) {
        return new QuestProgress(
                playerId, questId, objectiveProgress, completed, completedAt, claimId, claimedAt, newRevision);
    }
}

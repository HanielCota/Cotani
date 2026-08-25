package com.cotani.quest.api;

import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable successful quest claim that can be handed to the reward service. */
public record QuestClaim(QuestClaimId claimId, UUID playerId, QuestId questId, RewardId rewardId, Instant claimedAt) {
    public QuestClaim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(rewardId, "rewardId");
        Objects.requireNonNull(claimedAt, "claimedAt");
    }

    /** Reuses the quest idempotency key for the corresponding reward claim. */
    public RewardClaimId rewardClaimId() {
        return new RewardClaimId(claimId.value());
    }
}

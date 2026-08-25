package com.cotani.achievement.api;

import com.cotani.reward.api.RewardClaimId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable per-player achievement unlock state and persistence revision. */
public record AchievementProgress(
        UUID playerId,
        AchievementId achievementId,
        boolean unlocked,
        Optional<Instant> unlockedAt,
        Optional<RewardClaimId> rewardClaimId,
        long revision) {
    public AchievementProgress {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        Objects.requireNonNull(unlockedAt, "unlockedAt");
        Objects.requireNonNull(rewardClaimId, "rewardClaimId");
        if (unlocked != unlockedAt.isPresent()) {
            throw new IllegalArgumentException("unlocked and unlockedAt must agree");
        }
        if (rewardClaimId.isPresent() && !unlocked) {
            throw new IllegalArgumentException("rewardClaimId requires an unlocked achievement");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }

    public static AchievementProgress initial(UUID playerId, AchievementId achievementId) {
        return new AchievementProgress(playerId, achievementId, false, Optional.empty(), Optional.empty(), 0);
    }

    public boolean hasRewardClaim() {
        return rewardClaimId.isPresent();
    }

    public AchievementProgress withRevision(long newRevision) {
        return new AchievementProgress(playerId, achievementId, unlocked, unlockedAt, rewardClaimId, newRevision);
    }
}

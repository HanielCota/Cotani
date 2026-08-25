package com.cotani.achievement.api.event;

import com.cotani.achievement.api.AchievementId;
import com.cotani.achievement.api.AchievementProgress;
import com.cotani.event.api.CotaniEvent;
import com.cotani.reward.api.RewardId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Published once after an achievement unlock is durably accepted. */
public record AchievementUnlockedEvent(
        UUID playerId, AchievementId achievementId, Optional<RewardId> rewardId, AchievementProgress progress)
        implements CotaniEvent {
    public AchievementUnlockedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        Objects.requireNonNull(rewardId, "rewardId");
        Objects.requireNonNull(progress, "progress");
        if (!progress.unlocked()
                || !progress.playerId().equals(playerId)
                || !progress.achievementId().equals(achievementId)) {
            throw new IllegalArgumentException("progress must be the unlocked event player and achievement");
        }
        if (rewardId.isPresent() != progress.rewardClaimId().isPresent()) {
            throw new IllegalArgumentException("rewardId and progress rewardClaimId must agree");
        }
    }
}

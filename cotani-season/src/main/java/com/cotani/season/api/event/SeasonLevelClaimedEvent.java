package com.cotani.season.api.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.reward.api.RewardClaim;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonProgress;
import java.util.Objects;
import java.util.UUID;

/** Published after a season level reward has been durably claimed. */
public record SeasonLevelClaimedEvent(
        UUID playerId, SeasonId seasonId, int level, RewardClaim rewardClaim, SeasonProgress progress)
        implements CotaniEvent {
    public SeasonLevelClaimedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(rewardClaim, "rewardClaim");
        Objects.requireNonNull(progress, "progress");
        if (level <= 0) {
            throw new IllegalArgumentException("level must be positive");
        }
        if (!progress.playerId().equals(playerId)
                || !progress.seasonId().equals(seasonId)
                || !progress.isClaimed(level)) {
            throw new IllegalArgumentException("progress does not contain the claimed season level");
        }
        if (!rewardClaim.playerId().equals(playerId)) {
            throw new IllegalArgumentException("reward claim must belong to the event player");
        }
    }
}

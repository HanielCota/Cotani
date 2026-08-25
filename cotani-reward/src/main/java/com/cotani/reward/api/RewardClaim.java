package com.cotani.reward.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable successful claim receipt and snapshot of the grants to settle. */
public record RewardClaim(
        RewardClaimId claimId,
        UUID playerId,
        RewardId rewardId,
        Instant claimedAt,
        Instant nextAvailableAt,
        int streak,
        long totalClaims,
        List<RewardGrant> grants) {
    public RewardClaim {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(nextAvailableAt, "nextAvailableAt");
        Objects.requireNonNull(grants, "grants");
        if (!nextAvailableAt.isAfter(claimedAt)) {
            throw new IllegalArgumentException("nextAvailableAt must be after claimedAt");
        }
        if (streak <= 0 || totalClaims <= 0) {
            throw new IllegalArgumentException("streak and totalClaims must be positive");
        }
        if (grants.isEmpty() || grants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("grants must not be empty or contain null values");
        }
        grants = List.copyOf(grants);
    }
}

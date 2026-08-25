package com.cotani.reward.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Complete immutable input for an atomic repository claim. */
public record RewardClaimCommand(RewardClaimId claimId, UUID playerId, RewardDefinition definition, Instant now) {
    public RewardClaimCommand {
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(now, "now");
    }
}

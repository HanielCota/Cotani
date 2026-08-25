package com.cotani.reward.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Delivers one immutable reward grant to its target player. */
public interface RewardGrantHandler {
    /**
     * Returns whether this handler owns the supplied grant type.
     *
     * @param grant grant to inspect
     * @return true when this handler can deliver the grant
     */
    boolean supports(RewardGrant grant);

    /**
     * Delivers a grant. Implementations must make the operation idempotent using the context key.
     *
     * @param context immutable settlement context
     * @param grant grant to deliver
     * @return completion stage completed after delivery
     */
    CompletionStage<Void> settleAsync(RewardSettlementContext context, RewardGrant grant);

    /** Immutable identifiers used to make delivery retries safe. */
    record RewardSettlementContext(UUID playerId, RewardClaimId claimId, RewardId rewardId, int grantIndex) {
        public RewardSettlementContext {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(claimId, "claimId");
            Objects.requireNonNull(rewardId, "rewardId");
            if (grantIndex < 0) {
                throw new IllegalArgumentException("grantIndex must not be negative");
            }
        }
    }
}

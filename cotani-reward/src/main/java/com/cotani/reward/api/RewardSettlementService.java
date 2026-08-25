package com.cotani.reward.api;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates grant delivery and acknowledges a claim only after every grant succeeds.
 */
public interface RewardSettlementService {
    /** Claims and settles a reward in one asynchronous workflow. */
    CompletionStage<RewardClaim> claimAndSettleAsync(UUID playerId, RewardId rewardId);

    /** Recovers this player's pending claim for the reward, or creates a new claim when none exists. */
    CompletionStage<RewardClaim> claimOrRecoverAsync(UUID playerId, RewardId rewardId);

    /** Settles a previously created claim, including a claim recovered after a restart. */
    CompletionStage<RewardClaim> settleAsync(RewardClaim claim);

    /** Loads and settles a bounded batch of pending claims. */
    CompletionStage<List<RewardClaim>> settlePendingAsync(int limit);
}

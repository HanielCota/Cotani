package com.cotani.reward.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Bukkit-free persistence SPI for atomic, idempotent reward claims. */
public interface RewardRepository {
    /**
     * Atomically evaluates cooldown/streak state and stores a claim receipt.
     * Reusing a claim id for the same player and reward returns the original receipt.
     */
    CompletionStage<RewardClaim> claimAsync(RewardClaimCommand command);

    /** Loads a bounded oldest-first list of claims that still need settlement. */
    default CompletionStage<List<RewardClaim>> pendingClaimsAsync(int limit) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This reward repository does not support pending claim recovery"));
    }

    /** Finds the oldest unsettled claim for one player and reward without scanning unrelated claims. */
    default CompletionStage<Optional<RewardClaim>> findPendingClaimAsync(UUID playerId, RewardId rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        return pendingClaimsAsync(1_000)
                .thenApply(pending -> pending.stream()
                        .filter(claim -> claim.playerId().equals(playerId)
                                && claim.rewardId().equals(rewardId))
                        .findFirst());
    }

    /** Acknowledges a durable settlement; repeating the acknowledgement is idempotent. Returns false when unknown. */
    default CompletionStage<Boolean> markSettledAsync(RewardClaimId claimId) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                "This reward repository does not support settlement acknowledgements"));
    }

    /** Removes old settled idempotency receipts; pending receipts and player state are retained. */
    CompletionStage<Void> purgeClaimsBeforeAsync(Instant cutoff);
}

package com.cotani.reward.api;

import com.cotani.AsyncCloseable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Asynchronous reward registration and idempotent claim use cases. */
public interface RewardService extends AsyncCloseable, AutoCloseable {
    /** Registers a definition; duplicate ids are rejected. */
    void register(RewardDefinition definition);

    /** Returns a registered definition, if present. */
    Optional<RewardDefinition> findDefinition(RewardId id);

    /** Claims a reward with a newly generated idempotency key. */
    CompletionStage<RewardClaim> claimAsync(UUID playerId, RewardId rewardId);

    /** Claims a reward using a caller-owned idempotency key suitable for settlement retries. */
    CompletionStage<RewardClaim> claimAsync(UUID playerId, RewardId rewardId, RewardClaimId claimId);

    /** Loads bounded claims whose grants have not yet been acknowledged as settled. */
    default CompletionStage<List<RewardClaim>> pendingClaimsAsync(int limit) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This reward service does not support pending claim recovery"));
    }

    /** Finds an unsettled claim for one player and reward without scanning a bounded global page. */
    default CompletionStage<Optional<RewardClaim>> findPendingClaimAsync(UUID playerId, RewardId rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        return pendingClaimsAsync(1_000)
                .thenApply(pending -> pending.stream()
                        .filter(claim -> claim.playerId().equals(playerId)
                                && claim.rewardId().equals(rewardId))
                        .findFirst());
    }

    /** Acknowledges delivery of a claim; repeating the acknowledgement is safe. Returns false when unknown. */
    default CompletionStage<Boolean> markSettledAsync(RewardClaimId claimId) {
        return CompletableFuture.failedFuture(
                new UnsupportedOperationException("This reward service does not support settlement acknowledgements"));
    }

    /** Removes old claim receipts according to the configured retention period. */
    CompletionStage<Void> purgeClaimsAsync();

    /** Starts asynchronous shutdown and rejects new operations. */
    @Override
    void close();
}

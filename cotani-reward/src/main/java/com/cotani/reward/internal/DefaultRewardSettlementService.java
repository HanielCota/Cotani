package com.cotani.reward.internal;

import com.cotani.api.InternalApi;
import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardGrant;
import com.cotani.reward.api.RewardGrantHandler;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardService;
import com.cotani.reward.api.RewardSettlementService;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

@InternalApi
public final class DefaultRewardSettlementService implements RewardSettlementService {
    private final RewardService rewardService;
    private final List<RewardGrantHandler> handlers;
    private final ConcurrentHashMap<com.cotani.reward.api.RewardClaimId, CompletionStage<RewardClaim>> inFlight =
            new ConcurrentHashMap<>();

    public DefaultRewardSettlementService(RewardService rewardService, List<RewardGrantHandler> handlers) {
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
        Objects.requireNonNull(handlers, "handlers");
        if (handlers.isEmpty()) {
            throw new IllegalArgumentException("handlers must not be empty");
        }
        if (handlers.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("handlers must not contain null values");
        }
        this.handlers = List.copyOf(handlers);
    }

    @Override
    public CompletionStage<RewardClaim> claimAndSettleAsync(UUID playerId, RewardId rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        return rewardService.claimAsync(playerId, rewardId).thenCompose(this::settleAsync);
    }

    @Override
    public CompletionStage<RewardClaim> claimOrRecoverAsync(UUID playerId, RewardId rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        return rewardService
                .pendingClaimsAsync(1_000)
                .thenCompose(pending -> pending.stream()
                        .filter(claim -> claim.playerId().equals(playerId)
                                && claim.rewardId().equals(rewardId))
                        .findFirst()
                        .map(this::settleAsync)
                        .orElseGet(() -> claimAndSettleAsync(playerId, rewardId)));
    }

    @Override
    public CompletionStage<RewardClaim> settleAsync(RewardClaim claim) {
        Objects.requireNonNull(claim, "claim");
        var existing = inFlight.get(claim.claimId());
        if (existing != null) {
            return existing;
        }

        var created = settleOnceAsync(claim);
        var previous = inFlight.putIfAbsent(claim.claimId(), created);
        if (previous != null) {
            return previous;
        }
        var _ = created.whenComplete((ignored, failure) -> inFlight.remove(claim.claimId(), created));
        return created;
    }

    @Override
    public CompletionStage<List<RewardClaim>> settlePendingAsync(int limit) {
        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        return rewardService.pendingClaimsAsync(limit).thenCompose(this::settleBatchAsync);
    }

    private CompletionStage<RewardClaim> settleOnceAsync(RewardClaim claim) {
        return settleGrantAsync(claim, 0)
                .thenCompose(ignored -> rewardService.markSettledAsync(claim.claimId()))
                .thenApply(ignored -> claim);
    }

    private CompletionStage<Void> settleGrantAsync(RewardClaim claim, int index) {
        if (index >= claim.grants().size()) {
            return CompletableFuture.completedFuture(null);
        }
        var grant = claim.grants().get(index);
        var handler = findHandler(grant);
        var context = new RewardGrantHandler.RewardSettlementContext(
                claim.playerId(), claim.claimId(), claim.rewardId(), index);
        return handler.settleAsync(context, grant).thenCompose(ignored -> settleGrantAsync(claim, index + 1));
    }

    private CompletionStage<List<RewardClaim>> settleBatchAsync(List<RewardClaim> pending) {
        CompletionStage<List<RewardClaim>> result = CompletableFuture.completedFuture(new ArrayList<>());
        for (var claim : pending) {
            result = result.thenCompose(settled -> settleAsync(claim).thenApply(value -> {
                settled.add(value);
                return settled;
            }));
        }
        return result.thenApply(List::copyOf);
    }

    private RewardGrantHandler findHandler(RewardGrant grant) {
        return handlers.stream()
                .filter(handler -> handler.supports(grant))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No reward grant handler registered for "
                        + grant.getClass().getName()));
    }
}

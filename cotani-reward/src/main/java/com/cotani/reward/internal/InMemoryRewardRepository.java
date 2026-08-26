package com.cotani.reward.internal;

import com.cotani.api.InternalApi;
import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardClaimCommand;
import com.cotani.reward.api.RewardClaimConflictException;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardOnCooldownException;
import com.cotani.reward.api.RewardRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class InMemoryRewardRepository implements RewardRepository {
    private final Map<com.cotani.reward.api.RewardClaimId, RewardClaim> claims = new HashMap<>();
    private final java.util.Set<com.cotani.reward.api.RewardClaimId> settledClaims = new java.util.HashSet<>();
    private final Map<StateKey, State> states = new HashMap<>();

    @Override
    public synchronized CompletionStage<RewardClaim> claimAsync(RewardClaimCommand command) {
        Objects.requireNonNull(command, "command");
        var previousClaim = claims.get(command.claimId());
        if (previousClaim != null) {
            if (!previousClaim.playerId().equals(command.playerId())
                    || !previousClaim.rewardId().equals(command.definition().id())) {
                return CompletableFuture.failedFuture(new RewardClaimConflictException(command.claimId()));
            }
            return CompletableFuture.completedFuture(previousClaim);
        }

        var definition = command.definition();
        var key = new StateKey(command.playerId(), definition.id());
        var previousState = states.get(key);
        if (previousState != null) {
            var availableAt = previousState.lastClaimAt().plus(definition.cooldown());
            if (command.now().isBefore(availableAt)) {
                return CompletableFuture.failedFuture(
                        new RewardOnCooldownException(command.playerId(), definition.id(), availableAt));
            }
        }

        var streak = nextStreak(previousState, definition, command.now());
        var totalClaims = previousState == null ? 1L : Math.addExact(previousState.totalClaims(), 1L);
        var claim = new RewardClaim(
                command.claimId(),
                command.playerId(),
                definition.id(),
                command.now(),
                command.now().plus(definition.cooldown()),
                streak,
                totalClaims,
                definition.grants());
        claims.put(command.claimId(), claim);
        states.put(key, new State(command.now(), streak, totalClaims));
        return CompletableFuture.completedFuture(claim);
    }

    @Override
    public synchronized CompletionStage<List<RewardClaim>> pendingClaimsAsync(int limit) {
        validateLimit(limit);
        var pending = claims.entrySet().stream()
                .filter(entry -> !settledClaims.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(RewardClaim::claimedAt)
                        .thenComparing(claim -> claim.claimId().value().toString()))
                .limit(limit)
                .toList();
        return CompletableFuture.completedFuture(List.copyOf(pending));
    }

    @Override
    public synchronized CompletionStage<java.util.Optional<RewardClaim>> findPendingClaimAsync(
            UUID playerId, com.cotani.reward.api.RewardId rewardId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        var pending = claims.values().stream()
                .filter(claim -> claim.playerId().equals(playerId)
                        && claim.rewardId().equals(rewardId)
                        && !settledClaims.contains(claim.claimId()))
                .sorted(Comparator.comparing(RewardClaim::claimedAt)
                        .thenComparing(claim -> claim.claimId().value().toString()))
                .findFirst();
        return CompletableFuture.completedFuture(pending);
    }

    @Override
    public synchronized CompletionStage<Boolean> markSettledAsync(com.cotani.reward.api.RewardClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        if (!claims.containsKey(claimId)) {
            return CompletableFuture.completedFuture(false);
        }
        settledClaims.add(claimId);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public synchronized CompletionStage<Void> purgeClaimsBeforeAsync(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        claims.entrySet()
                .removeIf(entry -> settledClaims.contains(entry.getKey())
                        && entry.getValue().claimedAt().isBefore(cutoff));
        settledClaims.removeIf(claimId -> !claims.containsKey(claimId));
        return CompletableFuture.completedFuture(null);
    }

    private static void validateLimit(int limit) {
        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private static int nextStreak(@Nullable State previous, RewardDefinition definition, Instant now) {
        if (previous == null) {
            return 1;
        }
        var streakDeadline = previous.lastClaimAt().plus(definition.streakWindow());
        if (now.isAfter(streakDeadline)) {
            return 1;
        }
        return previous.streak() >= definition.maxStreak() ? definition.maxStreak() : previous.streak() + 1;
    }

    private record StateKey(UUID playerId, com.cotani.reward.api.RewardId rewardId) {}

    private record State(Instant lastClaimAt, int streak, long totalClaims) {}
}

package com.cotani.reward.internal;

import com.cotani.api.InternalApi;
import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardDefinition;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardNotFoundException;
import com.cotani.reward.api.RewardRepository;
import com.cotani.reward.api.RewardService;
import com.cotani.reward.api.RewardServiceOptions;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

@InternalApi
public final class DefaultRewardService implements RewardService {
    private static final Logger LOGGER = Logger.getLogger(DefaultRewardService.class.getName());

    private final RewardRepository repository;
    private final RewardServiceOptions options;
    private final Clock clock;
    private final java.util.concurrent.ConcurrentHashMap<RewardId, RewardDefinition> definitions =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    private DefaultRewardService(RewardRepository repository, RewardServiceOptions options, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultRewardService create(RewardRepository repository, RewardServiceOptions options, Clock clock) {
        return new DefaultRewardService(repository, options, clock);
    }

    @Override
    public void register(RewardDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (lifecycleLock) {
            ensureOpen();
            var previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Reward is already registered: " + definition.id().value());
            }
        }
    }

    @Override
    public CompletionStage<List<RewardClaim>> pendingClaimsAsync(int limit) {
        validateLimit(limit);
        CompletionStage<Void> pendingMutations;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            pendingMutations = sequencingTail;
        }
        var pending = pendingMutations.thenCompose(ignored -> repository.pendingClaimsAsync(limit));
        return options.withRepositoryTimeout(pending);
    }

    @Override
    public CompletionStage<Boolean> markSettledAsync(RewardClaimId claimId) {
        Objects.requireNonNull(claimId, "claimId");
        return enqueue(() -> {
            var durable = repository.markSettledAsync(claimId);
            return new Mutation<>(options.withRepositoryTimeout(durable), durable.thenApply(ignored -> null));
        });
    }

    @Override
    public Optional<RewardDefinition> findDefinition(RewardId id) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(id, "id")));
    }

    @Override
    public CompletionStage<RewardClaim> claimAsync(UUID playerId, RewardId rewardId) {
        return claimAsync(playerId, rewardId, RewardClaimId.random());
    }

    @Override
    public CompletionStage<RewardClaim> claimAsync(UUID playerId, RewardId rewardId, RewardClaimId claimId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(rewardId, "rewardId");
        Objects.requireNonNull(claimId, "claimId");
        var definition = definitions.get(rewardId);
        if (definition == null) {
            return failed(new RewardNotFoundException(rewardId));
        }
        return enqueue(() -> {
            var command = new com.cotani.reward.api.RewardClaimCommand(claimId, playerId, definition, clock.instant());
            var durable = repository.claimAsync(command);
            var visible = options.withRepositoryTimeout(durable);
            return new Mutation<>(visible, durable.thenApply(ignored -> null));
        });
    }

    @Override
    public CompletionStage<Void> purgeClaimsAsync() {
        return enqueue(() -> {
            Instant cutoff = clock.instant().minus(options.claimRetention());
            var durable = repository.purgeClaimsBeforeAsync(cutoff);
            return new Mutation<>(options.withRepositoryTimeout(durable), durable);
        });
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = lastOperation;
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close reward service", failure);
            }
        });
    }

    private <T> CompletionStage<T> enqueue(Supplier<Mutation<T>> operation) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }

            var result = new CompletableFuture<T>();
            var barrier = new CompletableFuture<Void>();
            var predecessor = sequencingTail;
            predecessor.whenComplete((ignored, ignoredFailure) -> {
                Mutation<T> mutation;
                try {
                    mutation = Objects.requireNonNull(operation.get(), "operation");
                } catch (RuntimeException operationFailure) {
                    result.completeExceptionally(operationFailure);
                    barrier.completeExceptionally(operationFailure);
                    return;
                }
                mutation.result().whenComplete((value, operationFailure) -> {
                    if (operationFailure == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(operationFailure);
                    }
                });
                mutation.barrier().whenComplete((value, operationFailure) -> {
                    if (operationFailure == null) {
                        barrier.complete(null);
                    } else {
                        barrier.completeExceptionally(operationFailure);
                    }
                });
            });
            sequencingTail = barrier.handle((ignored, ignoredFailure) -> null);
            lastOperation = barrier;
            return result;
        }
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Reward service is closed");
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw closedFailure();
        }
    }

    private static void validateLimit(int limit) {
        if (limit <= 0 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
    }

    private record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        private Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }
}

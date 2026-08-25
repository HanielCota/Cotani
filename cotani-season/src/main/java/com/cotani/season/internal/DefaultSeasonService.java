package com.cotani.season.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardService;
import com.cotani.season.api.SeasonDefinition;
import com.cotani.season.api.SeasonExperienceCommand;
import com.cotani.season.api.SeasonExperienceId;
import com.cotani.season.api.SeasonId;
import com.cotani.season.api.SeasonLevelLockedException;
import com.cotani.season.api.SeasonNotActiveException;
import com.cotani.season.api.SeasonNotFoundException;
import com.cotani.season.api.SeasonProgress;
import com.cotani.season.api.SeasonProgressConflictException;
import com.cotani.season.api.SeasonRepository;
import com.cotani.season.api.SeasonService;
import com.cotani.season.api.SeasonServiceOptions;
import com.cotani.season.api.event.SeasonExperienceAddedEvent;
import com.cotani.season.api.event.SeasonLevelClaimedEvent;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Default seasonal progression service with per-player serialization. */
@InternalApi
public final class DefaultSeasonService implements SeasonService {
    private static final Logger LOGGER = Logger.getLogger(DefaultSeasonService.class.getName());

    private final SeasonRepository repository;
    private final RewardService rewardService;
    private final EventBus eventBus;
    private final SeasonServiceOptions options;
    private final Clock clock;
    private final Map<SeasonId, SeasonDefinition> definitions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Map<MutationKey, CompletionStage<Void>> sequencingTails = new HashMap<>();
    private int pendingMutationCount;
    private @Nullable CompletableFuture<Void> closeStage;

    private DefaultSeasonService(
            SeasonRepository repository,
            RewardService rewardService,
            EventBus eventBus,
            SeasonServiceOptions options,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultSeasonService create(
            SeasonRepository repository,
            RewardService rewardService,
            EventBus eventBus,
            SeasonServiceOptions options,
            Clock clock) {
        return new DefaultSeasonService(repository, rewardService, eventBus, options, clock);
    }

    @Override
    public void register(SeasonDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (lifecycleLock) {
            ensureOpen();
            var previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Season is already registered: " + definition.id().value());
            }
        }
    }

    @Override
    public Optional<SeasonDefinition> findDefinition(SeasonId seasonId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(seasonId, "seasonId")));
    }

    @Override
    public Optional<SeasonDefinition> findActiveDefinition(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return definitions.values().stream()
                .filter(definition -> definition.acceptsExperienceAt(instant))
                .max(Comparator.comparing(SeasonDefinition::startsAt)
                        .thenComparing(definition -> definition.id().value()));
    }

    @Override
    public CompletionStage<Optional<SeasonProgress>> findProgressAsync(UUID playerId, SeasonId seasonId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        if (!definitions.containsKey(seasonId)) {
            return failed(new SeasonNotFoundException(seasonId));
        }
        CompletionStage<Void> pending;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            pending = sequencingTails.getOrDefault(new MutationKey(playerId, seasonId), completedVoid());
        }
        return options.withRepositoryTimeout(pending.thenCompose(ignored -> repository.findAsync(playerId, seasonId)));
    }

    @Override
    public CompletionStage<SeasonProgress> addExperienceAsync(
            UUID playerId, SeasonId seasonId, long amount, SeasonExperienceId operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(operationId, "operationId");
        var definition = definitions.get(seasonId);
        if (definition == null) {
            return failed(new SeasonNotFoundException(seasonId));
        }
        var command = new SeasonExperienceCommand(playerId, seasonId, amount, operationId, clock.instant());
        if (!definition.acceptsExperienceAt(command.occurredAt())) {
            return failed(new SeasonNotActiveException(seasonId, command.occurredAt()));
        }
        return enqueue(
                () -> {
                    var durable = applyExperienceWithRetry(definition, command, 1);
                    return new Mutation<>(durable, durable.thenApply(ignored -> null));
                },
                new MutationKey(playerId, seasonId));
    }

    @Override
    public CompletionStage<RewardClaim> claimLevelAsync(UUID playerId, SeasonId seasonId, int level) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        var definition = definitions.get(seasonId);
        if (definition == null) {
            return failed(new SeasonNotFoundException(seasonId));
        }
        var seasonLevel = definition.findLevel(level);
        if (seasonLevel.isEmpty()) {
            return failed(new IllegalArgumentException("Unknown season level: " + level));
        }
        return enqueue(
                () -> {
                    var durable = claimLevelWithRetry(playerId, definition, seasonLevel.orElseThrow(), 1);
                    return new Mutation<>(durable, durable.thenApply(ignored -> null));
                },
                new MutationKey(playerId, seasonId));
    }

    @Override
    public CompletionStage<Void> purgeExperienceOperationsAsync(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
        }
        return options.withRepositoryTimeout(repository.purgeExperienceOperationsBeforeAsync(cutoff));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            var pending = sequencingTails.values().stream()
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            closeStage = CompletableFuture.allOf(pending);
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close season service", failure);
            }
        });
    }

    private CompletionStage<SeasonProgress> applyExperienceWithRetry(
            SeasonDefinition definition, SeasonExperienceCommand command, int attempt) {
        return options.withRepositoryTimeout(repository.applyExperienceAsync(command))
                .thenCompose(saved -> publishExperienceEvent(command, saved).thenApply(ignored -> saved))
                .exceptionallyCompose(failure -> {
                    var cause = unwrap(failure);
                    if (cause instanceof SeasonProgressConflictException && attempt < options.maxMutationAttempts()) {
                        return applyExperienceWithRetry(definition, command, attempt + 1);
                    }
                    return failed(Objects.requireNonNull(cause, "failure"));
                });
    }

    private CompletionStage<RewardClaim> claimLevelWithRetry(
            UUID playerId, SeasonDefinition definition, com.cotani.season.api.SeasonLevel level, int attempt) {
        return options.withRepositoryTimeout(repository.findAsync(playerId, definition.id()))
                .thenCompose(existing -> {
                    var current = existing.orElseGet(() -> SeasonProgress.initial(playerId, definition.id()));
                    if (current.experience() < level.requiredExperience()) {
                        return failed(new SeasonLevelLockedException(definition.id(), level.level()));
                    }
                    var claimId = rewardClaimId(playerId, definition.id(), level.level());
                    return rewardService
                            .claimAsync(playerId, level.rewardId(), claimId)
                            .thenCompose(rewardClaim -> {
                                if (current.isClaimed(level.level())) {
                                    return CompletableFuture.completedFuture(rewardClaim);
                                }
                                var next = current.claimLevel(level.level());
                                return options.withRepositoryTimeout(repository.saveAsync(next, current.revision()))
                                        .thenCompose(saved -> publishClaimedEvent(
                                                        playerId, definition.id(), level.level(), rewardClaim, saved)
                                                .thenApply(ignored -> rewardClaim));
                            });
                })
                .exceptionallyCompose(failure -> {
                    var cause = unwrap(failure);
                    if (cause instanceof SeasonProgressConflictException && attempt < options.maxMutationAttempts()) {
                        return claimLevelWithRetry(playerId, definition, level, attempt + 1);
                    }
                    return failed(Objects.requireNonNull(cause, "failure"));
                });
    }

    private CompletionStage<Void> publishExperienceEvent(SeasonExperienceCommand command, SeasonProgress progress) {
        return publishBestEffort(
                new SeasonExperienceAddedEvent(
                        command.playerId(), command.seasonId(), command.operationId(), command.amount(), progress),
                "season experience");
    }

    private CompletionStage<Void> publishClaimedEvent(
            UUID playerId, SeasonId seasonId, int level, RewardClaim rewardClaim, SeasonProgress progress) {
        return publishBestEffort(
                new SeasonLevelClaimedEvent(playerId, seasonId, level, rewardClaim, progress), "season level claim");
    }

    private <T extends com.cotani.event.api.CotaniEvent> CompletionStage<Void> publishBestEffort(
            T event, String eventName) {
        try {
            return options.withEventTimeout(eventBus.publishAsync(event)).handle((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.log(Level.WARNING, "Failed to publish " + eventName + " event", unwrap(failure));
                }
                return null;
            });
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to publish " + eventName + " event", failure);
            return completedVoid();
        }
    }

    private <T> CompletionStage<T> enqueue(Supplier<Mutation<T>> operation, MutationKey key) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            if (pendingMutationCount >= options.maxPendingMutations()) {
                return failed(new RejectedExecutionException("Season mutation queue is full"));
            }

            var result = new CompletableFuture<T>();
            var barrier = new CompletableFuture<Void>();
            pendingMutationCount++;
            var completion = barrier.whenComplete((ignored, failure) -> {
                synchronized (lifecycleLock) {
                    pendingMutationCount--;
                }
            });
            Objects.requireNonNull(completion, "completion");
            var predecessor = sequencingTails.getOrDefault(key, completedVoid());
            predecessor.whenComplete((ignored, ignoredFailure) -> {
                Mutation<T> mutation;
                try {
                    mutation = Objects.requireNonNull(operation.get(), "operation");
                } catch (RuntimeException operationFailure) {
                    result.completeExceptionally(operationFailure);
                    barrier.completeExceptionally(operationFailure);
                    return;
                }
                mutation.result().whenComplete((value, failure) -> {
                    if (failure == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(failure);
                    }
                });
                mutation.barrier().whenComplete((value, failure) -> {
                    if (failure == null) {
                        barrier.complete(null);
                    } else {
                        barrier.completeExceptionally(failure);
                    }
                });
            });
            CompletionStage<Void> nextTail = barrier.handle((ignored, ignoredFailure) -> (Void) null);
            sequencingTails.put(key, nextTail);
            nextTail.whenComplete((ignored, failure) -> {
                synchronized (lifecycleLock) {
                    if (Objects.equals(sequencingTails.get(key), nextTail)) {
                        sequencingTails.remove(key);
                    }
                }
            });
            return result;
        }
    }

    private static RewardClaimId rewardClaimId(UUID playerId, SeasonId seasonId, int level) {
        var source = "cotani-season:" + playerId + ":" + seasonId.value() + ":" + level;
        return new RewardClaimId(UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)));
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw closedFailure();
        }
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Season service is closed");
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(Objects.requireNonNull(failure, "failure"));
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    private static @Nullable Throwable unwrap(@Nullable Throwable failure) {
        if (failure == null) {
            return null;
        }
        var current = failure;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        private Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }

    private record MutationKey(UUID playerId, SeasonId seasonId) {}
}

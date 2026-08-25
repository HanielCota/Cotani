package com.cotani.statistics.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.statistics.api.StatisticConflictException;
import com.cotani.statistics.api.StatisticEntry;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.statistics.api.StatisticRanking;
import com.cotani.statistics.api.StatisticRepository;
import com.cotani.statistics.api.StatisticService;
import com.cotani.statistics.api.StatisticServiceOptions;
import com.cotani.statistics.api.StatisticUpdate;
import com.cotani.statistics.api.event.StatisticChangedEvent;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Default per-player statistic service with atomic repository updates and bounded rankings. */
@InternalApi
public final class DefaultStatisticService implements StatisticService {
    private static final Logger LOGGER = Logger.getLogger(DefaultStatisticService.class.getName());

    private final StatisticRepository repository;
    private final EventBus eventBus;
    private final StatisticServiceOptions options;
    private final Clock clock;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Map<StatisticKey, CompletionStage<Void>> sequencingTails = new HashMap<>();
    private int pendingMutationCount;
    private @Nullable CompletableFuture<Void> closeStage;

    private DefaultStatisticService(
            StatisticRepository repository, EventBus eventBus, StatisticServiceOptions options, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultStatisticService create(
            StatisticRepository repository, EventBus eventBus, StatisticServiceOptions options, Clock clock) {
        return new DefaultStatisticService(repository, eventBus, options, clock);
    }

    @Override
    public CompletionStage<Optional<StatisticEntry>> findAsync(UUID playerId, StatisticId statisticId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        var key = new StatisticKey(playerId, statisticId);
        CompletionStage<Void> pending;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            pending = sequencingTails.getOrDefault(key, completedVoid());
        }
        return options.withRepositoryTimeout(
                pending.thenCompose(ignored -> repository.findAsync(playerId, statisticId)));
    }

    @Override
    public CompletionStage<StatisticEntry> incrementAsync(UUID playerId, StatisticId statisticId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        if (amount <= 0) {
            return failed(new IllegalArgumentException("amount must be positive"));
        }
        var key = new StatisticKey(playerId, statisticId);
        return enqueue(key, () -> {
            var durable = incrementWithRetry(playerId, statisticId, amount, null, 1);
            return new Mutation<>(durable.thenApply(StatisticUpdate::current), durable.thenApply(ignored -> null));
        });
    }

    @Override
    public CompletionStage<StatisticEntry> incrementAsync(
            UUID playerId, StatisticId statisticId, long amount, StatisticOperationId operationId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(operationId, "operationId");
        if (amount <= 0) {
            return failed(new IllegalArgumentException("amount must be positive"));
        }
        var key = new StatisticKey(playerId, statisticId);
        return enqueue(key, () -> {
            var durable = incrementWithRetry(playerId, statisticId, amount, operationId, 1);
            return new Mutation<>(durable.thenApply(StatisticUpdate::current), durable.thenApply(ignored -> null));
        });
    }

    @Override
    public CompletionStage<StatisticRanking> topAsync(StatisticId statisticId, int limit) {
        Objects.requireNonNull(statisticId, "statisticId");
        if (limit <= 0 || limit > 1_000) {
            return failed(new IllegalArgumentException("limit must be between 1 and 1000"));
        }
        CompletionStage<Void> pending;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            var tails = sequencingTails.entrySet().stream()
                    .filter(entry -> entry.getKey().statisticId().equals(statisticId))
                    .map(Map.Entry::getValue)
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            pending = CompletableFuture.allOf(tails);
        }
        return options.withRepositoryTimeout(pending.thenCompose(ignored -> repository.topAsync(statisticId, limit)))
                .thenApply(entries -> new StatisticRanking(statisticId, entries));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            var tails = sequencingTails.values().stream()
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            closeStage = CompletableFuture.allOf(tails);
            return closeStage;
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close statistics service", failure);
            }
        });
    }

    private CompletionStage<StatisticUpdate> incrementWithRetry(
            UUID playerId,
            StatisticId statisticId,
            long amount,
            @Nullable StatisticOperationId operationId,
            int attempt) {
        CompletionStage<StatisticUpdate> repositoryStage;
        try {
            var updatedAt = clock.instant();
            repositoryStage = Objects.requireNonNull(
                    operationId == null
                            ? repository.incrementAsync(playerId, statisticId, amount, updatedAt)
                            : repository.incrementIdempotentlyAsync(
                                    playerId, statisticId, amount, updatedAt, operationId),
                    "repository returned null stage");
        } catch (RuntimeException failure) {
            return failed(failure);
        }
        CompletionStage<StatisticUpdate> timedRepositoryStage;
        try {
            timedRepositoryStage = options.withRepositoryTimeout(repositoryStage);
        } catch (RuntimeException failure) {
            return failed(failure);
        }
        return timedRepositoryStage
                .handle((update, failure) -> new UpdateOutcome(update, unwrap(failure)))
                .thenCompose(outcome -> {
                    if (outcome.failure() != null) {
                        if (outcome.failure() instanceof TimeoutException) {
                            observeLateRepositoryResult(repositoryStage, playerId, statisticId);
                        }
                        if (outcome.failure() instanceof StatisticConflictException
                                && attempt < options.maxMutationAttempts()) {
                            return incrementWithRetry(playerId, statisticId, amount, operationId, attempt + 1);
                        }
                        return failed(outcome.failure());
                    }
                    var update = Objects.requireNonNull(outcome.update(), "repository returned null update");
                    if (!update.newlyApplied()) {
                        return CompletableFuture.completedFuture(update);
                    }
                    return publishBestEffort(
                                    new StatisticChangedEvent(
                                            playerId,
                                            statisticId,
                                            update.amount(),
                                            update.previousValue(),
                                            update.current()),
                                    "statistic change")
                            .thenApply(ignored -> update);
                });
    }

    private void observeLateRepositoryResult(
            CompletionStage<StatisticUpdate> repositoryStage, UUID playerId, StatisticId statisticId) {
        repositoryStage.whenComplete((update, failure) -> {
            var unwrappedFailure = unwrap(failure);
            if (unwrappedFailure != null) {
                LOGGER.log(Level.WARNING, "Statistic mutation failed after its timeout", unwrappedFailure);
                return;
            }
            if (update == null) {
                LOGGER.warning("Statistic repository completed with a null update after its timeout");
                return;
            }
            if (!update.newlyApplied()) {
                return;
            }
            publishBestEffort(
                    new StatisticChangedEvent(
                            playerId, statisticId, update.amount(), update.previousValue(), update.current()),
                    "late statistic change");
        });
    }

    private <T> CompletionStage<T> enqueue(StatisticKey key, Supplier<Mutation<T>> operation) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            if (pendingMutationCount >= options.maxPendingMutations()) {
                return failed(new RejectedExecutionException("Statistics mutation queue is full"));
            }
            pendingMutationCount++;
            var result = new CompletableFuture<T>();
            var barrier = new CompletableFuture<Void>();
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
            var cleanup = nextTail.whenComplete((ignored, failure) -> {
                synchronized (lifecycleLock) {
                    if (Objects.equals(sequencingTails.get(key), nextTail)) {
                        sequencingTails.remove(key);
                    }
                }
            });
            Objects.requireNonNull(cleanup, "cleanup");
            return result;
        }
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

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Statistics service is closed");
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
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

    private record StatisticKey(UUID playerId, StatisticId statisticId) {}

    private record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        private Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }

    private record UpdateOutcome(
            @Nullable StatisticUpdate update, @Nullable Throwable failure) {}
}

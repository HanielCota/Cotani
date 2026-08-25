package com.cotani.quest.internal;

import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.quest.api.QuestAlreadyClaimedException;
import com.cotani.quest.api.QuestClaim;
import com.cotani.quest.api.QuestClaimId;
import com.cotani.quest.api.QuestDefinition;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestNotCompletedException;
import com.cotani.quest.api.QuestNotFoundException;
import com.cotani.quest.api.QuestObjective;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.quest.api.QuestProgress;
import com.cotani.quest.api.QuestProgressConflictException;
import com.cotani.quest.api.QuestRepository;
import com.cotani.quest.api.QuestService;
import com.cotani.quest.api.QuestServiceOptions;
import com.cotani.quest.api.event.QuestClaimedEvent;
import com.cotani.quest.api.event.QuestCompletedEvent;
import com.cotani.quest.api.event.QuestProgressedEvent;
import java.time.Clock;
import java.time.Instant;
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

/** Default per-player quest service with optimistic retry for external writers. */
@InternalApi
public final class DefaultQuestService implements QuestService {
    private static final Logger LOGGER = Logger.getLogger(DefaultQuestService.class.getName());

    private final QuestRepository repository;
    private final EventBus eventBus;
    private final QuestServiceOptions options;
    private final Clock clock;
    private final Map<QuestId, QuestDefinition> definitions = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final Map<MutationKey, CompletionStage<Void>> sequencingTails = new HashMap<>();
    private int pendingMutationCount;
    private @Nullable CompletableFuture<Void> closeStage;

    private DefaultQuestService(
            QuestRepository repository, EventBus eventBus, QuestServiceOptions options, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public static DefaultQuestService create(
            QuestRepository repository, EventBus eventBus, QuestServiceOptions options, Clock clock) {
        return new DefaultQuestService(repository, eventBus, options, clock);
    }

    @Override
    public void register(QuestDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (lifecycleLock) {
            ensureOpen();
            var previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Quest is already registered: " + definition.id().value());
            }
        }
    }

    @Override
    public Optional<QuestDefinition> findDefinition(QuestId questId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(questId, "questId")));
    }

    @Override
    public CompletionStage<Optional<QuestProgress>> findProgressAsync(UUID playerId, QuestId questId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        if (!definitions.containsKey(questId)) {
            return failed(new QuestNotFoundException(questId));
        }

        CompletionStage<Void> pending;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            pending = sequencingTails.getOrDefault(new MutationKey(playerId, questId), completedVoid());
        }
        return options.withRepositoryTimeout(pending.thenCompose(ignored -> repository.findAsync(playerId, questId)));
    }

    @Override
    public CompletionStage<QuestProgress> recordProgressAsync(
            UUID playerId, QuestId questId, QuestObjectiveId objectiveId, long amount) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(objectiveId, "objectiveId");
        if (amount <= 0) {
            return failed(new IllegalArgumentException("amount must be positive"));
        }
        var definition = definitions.get(questId);
        if (definition == null) {
            return failed(new QuestNotFoundException(questId));
        }
        var objective = definition.findObjective(objectiveId);
        if (objective.isEmpty()) {
            return failed(new IllegalArgumentException(
                    "Objective is not part of quest " + questId.value() + ": " + objectiveId.value()));
        }

        return enqueue(
                () -> {
                    var durable = advanceWithRetry(playerId, definition, objective.orElseThrow(), amount, 1);
                    return new Mutation<>(durable, durable.thenApply(ignored -> null));
                },
                new MutationKey(playerId, questId));
    }

    @Override
    public CompletionStage<QuestClaim> claimAsync(UUID playerId, QuestId questId, QuestClaimId claimId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(claimId, "claimId");
        var definition = definitions.get(questId);
        if (definition == null) {
            return failed(new QuestNotFoundException(questId));
        }

        return enqueue(
                () -> {
                    var durable = claimWithRetry(playerId, definition, claimId, 1);
                    return new Mutation<>(durable, durable.thenApply(ignored -> null));
                },
                new MutationKey(playerId, questId));
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
                LOGGER.log(Level.SEVERE, "Failed to close quest service", failure);
            }
        });
    }

    private CompletionStage<QuestProgress> advanceWithRetry(
            UUID playerId, QuestDefinition definition, QuestObjective objective, long amount, int attempt) {
        var loaded = repository.findAsync(playerId, definition.id());
        return loaded.thenCompose(existing -> {
            var current = existing.orElseGet(() -> QuestProgress.initial(playerId, definition.id()));
            if (current.completed()) {
                return CompletableFuture.completedFuture(current);
            }

            var next = advance(current, definition, objective, amount, clock.instant());
            if (next.equals(current)) {
                return CompletableFuture.completedFuture(current);
            }

            return repository
                    .saveAsync(next, current.revision())
                    .handle((saved, failure) -> new SaveOutcome(saved, unwrap(failure)))
                    .thenCompose(outcome -> {
                        if (outcome.failure() != null) {
                            if (outcome.failure() instanceof QuestProgressConflictException
                                    && attempt < options.maxMutationAttempts()) {
                                return advanceWithRetry(playerId, definition, objective, amount, attempt + 1);
                            }
                            return failed(outcome.failure());
                        }
                        var saved = Objects.requireNonNull(outcome.saved(), "repository returned null progress");
                        return publishProgressEvents(playerId, definition, objective.id(), current, saved)
                                .thenApply(ignored -> saved);
                    });
        });
    }

    private CompletionStage<QuestClaim> claimWithRetry(
            UUID playerId, QuestDefinition definition, QuestClaimId claimId, int attempt) {
        var loaded = repository.findAsync(playerId, definition.id());
        return loaded.thenCompose(existing -> {
            var current = existing.orElseGet(() -> QuestProgress.initial(playerId, definition.id()));
            if (!current.completed()) {
                return failed(new QuestNotCompletedException(playerId, definition.id()));
            }
            if (current.claimId().isPresent()) {
                if (!current.claimId().orElseThrow().equals(claimId)) {
                    return failed(new QuestAlreadyClaimedException(
                            playerId, definition.id(), current.claimId().orElseThrow()));
                }
                return CompletableFuture.completedFuture(toClaim(current, definition));
            }

            var claimedAt = clock.instant();
            var next = new QuestProgress(
                    current.playerId(),
                    current.questId(),
                    current.objectiveProgress(),
                    current.completed(),
                    current.completedAt(),
                    Optional.of(claimId),
                    Optional.of(claimedAt),
                    current.revision());
            return repository
                    .saveAsync(next, current.revision())
                    .handle((saved, failure) -> new SaveOutcome(saved, unwrap(failure)))
                    .thenCompose(outcome -> {
                        if (outcome.failure() != null) {
                            if (outcome.failure() instanceof QuestProgressConflictException
                                    && attempt < options.maxMutationAttempts()) {
                                return claimWithRetry(playerId, definition, claimId, attempt + 1);
                            }
                            return failed(outcome.failure());
                        }
                        var saved = Objects.requireNonNull(outcome.saved(), "repository returned null progress");
                        var claim = toClaim(saved, definition);
                        return publishClaimedEvent(claim).thenApply(ignored -> claim);
                    });
        });
    }

    private CompletionStage<Void> publishProgressEvents(
            UUID playerId,
            QuestDefinition definition,
            QuestObjectiveId objectiveId,
            QuestProgress previous,
            QuestProgress saved) {
        var appliedAmount = saved.progressFor(objectiveId) - previous.progressFor(objectiveId);
        var progressEvent = new QuestProgressedEvent(playerId, definition.id(), objectiveId, appliedAmount, saved);
        var published = publishBestEffort(progressEvent, "quest progress");
        if (!previous.completed() && saved.completed()) {
            return published.thenCompose(ignored ->
                    publishBestEffort(new QuestCompletedEvent(playerId, definition.id(), saved), "quest completion"));
        }
        return published;
    }

    private CompletionStage<Void> publishClaimedEvent(QuestClaim claim) {
        return publishBestEffort(new QuestClaimedEvent(claim), "quest claim");
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

    private static QuestProgress advance(
            QuestProgress current, QuestDefinition definition, QuestObjective objective, long amount, Instant now) {
        var currentAmount = Math.min(current.progressFor(objective.id()), objective.requiredAmount());
        var remaining = objective.requiredAmount() - currentAmount;
        var increment = Math.min(amount, remaining);
        if (increment <= 0) {
            return current;
        }

        var progress = new HashMap<>(current.objectiveProgress());
        progress.put(objective.id(), currentAmount + increment);
        var completed = definition.objectives().stream()
                .allMatch(candidate -> progress.getOrDefault(candidate.id(), 0L) >= candidate.requiredAmount());
        return new QuestProgress(
                current.playerId(),
                current.questId(),
                progress,
                completed,
                completed ? Optional.of(now) : Optional.empty(),
                current.claimId(),
                current.claimedAt(),
                current.revision());
    }

    private static QuestClaim toClaim(QuestProgress progress, QuestDefinition definition) {
        return new QuestClaim(
                progress.claimId().orElseThrow(),
                progress.playerId(),
                progress.questId(),
                definition.rewardId(),
                progress.claimedAt().orElseThrow());
    }

    private <T> CompletionStage<T> enqueue(Supplier<Mutation<T>> operation, MutationKey key) {
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            if (pendingMutationCount >= options.maxPendingMutations()) {
                return failed(new RejectedExecutionException("Quest mutation queue is full"));
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

    private void ensureOpen() {
        if (closed.get()) {
            throw closedFailure();
        }
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Quest service is closed");
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

    private record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        private Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }

    private record MutationKey(UUID playerId, QuestId questId) {}

    private record SaveOutcome(
            @Nullable QuestProgress saved, @Nullable Throwable failure) {}
}

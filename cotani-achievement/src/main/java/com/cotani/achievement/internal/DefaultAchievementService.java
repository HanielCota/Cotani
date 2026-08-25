package com.cotani.achievement.internal;

import com.cotani.achievement.api.AchievementDefinition;
import com.cotani.achievement.api.AchievementId;
import com.cotani.achievement.api.AchievementNotFoundException;
import com.cotani.achievement.api.AchievementNotUnlockedException;
import com.cotani.achievement.api.AchievementProgress;
import com.cotani.achievement.api.AchievementProgressConflictException;
import com.cotani.achievement.api.AchievementRepository;
import com.cotani.achievement.api.AchievementRewardNotConfiguredException;
import com.cotani.achievement.api.AchievementService;
import com.cotani.achievement.api.AchievementServiceOptions;
import com.cotani.achievement.api.event.AchievementUnlockedEvent;
import com.cotani.api.InternalApi;
import com.cotani.event.api.EventBus;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardService;
import com.cotani.statistics.api.StatisticEntry;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticService;
import com.cotani.statistics.api.event.StatisticChangedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/** Default achievement service with optimistic retries and per-player sequencing. */
@InternalApi
public final class DefaultAchievementService implements AchievementService {
    private static final Logger LOGGER = Logger.getLogger(DefaultAchievementService.class.getName());

    private final AchievementRepository repository;
    private final StatisticService statistics;
    private final RewardService rewards;
    private final EventBus eventBus;
    private final AchievementServiceOptions options;
    private final Clock clock;
    private final AchievementMutationQueue mutationQueue;
    private final ConcurrentHashMap<AchievementId, AchievementDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<StatisticId, Set<AchievementId>> definitionsByStatistic = new ConcurrentHashMap<>();
    private final Set<AchievementMutationQueue.Key> pendingUnlockEvents = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lifecycleLock = new Object();
    private final EventSubscription statisticSubscription;

    private DefaultAchievementService(
            AchievementRepository repository,
            StatisticService statistics,
            RewardService rewards,
            EventBus eventBus,
            AchievementServiceOptions options,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.rewards = Objects.requireNonNull(rewards, "rewards");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.options = Objects.requireNonNull(options, "options");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.mutationQueue = new AchievementMutationQueue(options.maxPendingMutations());
        this.statisticSubscription = Objects.requireNonNull(
                eventBus.subscribe(StatisticChangedEvent.class, this::onStatisticChanged), "statistic subscription");
    }

    public static DefaultAchievementService create(
            AchievementRepository repository,
            StatisticService statistics,
            RewardService rewards,
            EventBus eventBus,
            AchievementServiceOptions options,
            Clock clock) {
        return new DefaultAchievementService(repository, statistics, rewards, eventBus, options, clock);
    }

    @Override
    public void register(AchievementDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        synchronized (lifecycleLock) {
            ensureOpen();
            var previous = definitions.putIfAbsent(definition.id(), definition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Achievement is already registered: " + definition.id().value());
            }
            for (var criterion : definition.criteria()) {
                definitionsByStatistic
                        .computeIfAbsent(criterion.statisticId(), ignored -> ConcurrentHashMap.newKeySet())
                        .add(definition.id());
            }
        }
    }

    @Override
    public Optional<AchievementDefinition> findDefinition(AchievementId achievementId) {
        return Optional.ofNullable(definitions.get(Objects.requireNonNull(achievementId, "achievementId")));
    }

    @Override
    public CompletionStage<Optional<AchievementProgress>> findProgressAsync(
            UUID playerId, AchievementId achievementId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        if (!definitions.containsKey(achievementId)) {
            return failed(new AchievementNotFoundException(achievementId));
        }

        if (closed.get()) {
            return failed(closedFailure());
        }
        return options.withRepositoryTimeout(mutationQueue.await(
                new AchievementMutationQueue.Key(playerId, achievementId),
                () -> repository.findAsync(playerId, achievementId)));
    }

    @Override
    public CompletionStage<AchievementProgress> evaluateAsync(UUID playerId, AchievementId achievementId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        var definition = definitions.get(achievementId);
        if (definition == null) {
            return failed(new AchievementNotFoundException(achievementId));
        }
        return mutationQueue.enqueue(
                () -> {
                    var durable = evaluateWithRetry(playerId, definition, 1);
                    return new AchievementMutationQueue.Mutation<>(
                            options.withRepositoryTimeout(durable), durable.thenApply(ignored -> null));
                },
                new AchievementMutationQueue.Key(playerId, achievementId));
    }

    @Override
    public CompletionStage<RewardClaim> claimRewardAsync(UUID playerId, AchievementId achievementId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(achievementId, "achievementId");
        var definition = definitions.get(achievementId);
        if (definition == null) {
            return failed(new AchievementNotFoundException(achievementId));
        }
        if (definition.rewardId().isEmpty()) {
            return failed(new AchievementRewardNotConfiguredException(achievementId));
        }
        var rewardId = definition.rewardId().orElseThrow();
        return mutationQueue.enqueue(
                () -> {
                    var durable = repository
                            .findAsync(playerId, achievementId)
                            .thenCompose(
                                    progress -> claimRewardIfUnlocked(playerId, achievementId, rewardId, progress));
                    return new AchievementMutationQueue.Mutation<>(
                            options.withRepositoryTimeout(durable), durable.thenApply(ignored -> null));
                },
                new AchievementMutationQueue.Key(playerId, achievementId));
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        synchronized (lifecycleLock) {
            if (closed.compareAndSet(false, true)) {
                statisticSubscription.unsubscribe();
            }
            return mutationQueue.closeAsync();
        }
    }

    @Override
    public void close() {
        closeAsync().whenComplete((ignored, failure) -> {
            if (failure != null) {
                LOGGER.log(Level.SEVERE, "Failed to close achievement service", failure);
            }
        });
    }

    private CompletionStage<AchievementProgress> evaluateWithRetry(
            UUID playerId, AchievementDefinition definition, int attempt) {
        return repository.findAsync(playerId, definition.id()).thenCompose(existing -> {
            var current = existing.orElseGet(() -> AchievementProgress.initial(playerId, definition.id()));
            if (current.unlocked()) {
                return retryPendingUnlockEvent(playerId, definition, current).thenApply(ignored -> current);
            }
            return criteriaMetAsync(playerId, definition).thenCompose(criteriaMet -> {
                if (!criteriaMet) {
                    return CompletableFuture.completedFuture(current);
                }
                var next = unlockedProgress(current, definition, clock.instant());
                return repository
                        .saveAsync(next, current.revision())
                        .handle((saved, failure) -> new SaveOutcome(saved, unwrap(failure)))
                        .thenCompose(outcome -> handleSaveOutcome(outcome, playerId, definition, attempt));
            });
        });
    }

    private CompletionStage<AchievementProgress> handleSaveOutcome(
            SaveOutcome outcome, UUID playerId, AchievementDefinition definition, int attempt) {
        if (outcome.failure() != null) {
            if (outcome.failure() instanceof AchievementProgressConflictException
                    && attempt < options.maxMutationAttempts()) {
                return evaluateWithRetry(playerId, definition, attempt + 1);
            }
            return failed(outcome.failure());
        }
        var saved = Objects.requireNonNull(outcome.saved(), "repository returned null progress");
        return publishUnlockEvent(playerId, definition, saved).thenApply(ignored -> saved);
    }

    private CompletionStage<Boolean> criteriaMetAsync(UUID playerId, AchievementDefinition definition) {
        return criteriaMetAsync(playerId, definition, 0);
    }

    private CompletionStage<Boolean> criteriaMetAsync(UUID playerId, AchievementDefinition definition, int index) {
        if (index == definition.criteria().size()) {
            return CompletableFuture.completedFuture(true);
        }
        var criterion = definition.criteria().get(index);
        CompletionStage<Optional<StatisticEntry>> statistic = statistics.findAsync(playerId, criterion.statisticId());
        return Objects.requireNonNull(statistic, "statistics returned null stage")
                .thenCompose(entry -> {
                    if (entry.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    var current = entry.orElseThrow();
                    if (current.value() < criterion.requiredValue()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return criteriaMetAsync(playerId, definition, index + 1);
                });
    }

    private CompletionStage<RewardClaim> claimRewardIfUnlocked(
            UUID playerId, AchievementId achievementId, RewardId rewardId, Optional<AchievementProgress> progress) {
        if (progress.isEmpty()) {
            return failed(new AchievementNotUnlockedException(playerId, achievementId));
        }
        var current = progress.orElseThrow();
        if (!current.unlocked()) {
            return failed(new AchievementNotUnlockedException(playerId, achievementId));
        }
        var claimId =
                current.rewardClaimId().orElseThrow(() -> new AchievementRewardNotConfiguredException(achievementId));
        return Objects.requireNonNull(rewards.claimAsync(playerId, rewardId, claimId), "rewards returned null stage");
    }

    private AchievementProgress unlockedProgress(
            AchievementProgress current, AchievementDefinition definition, Instant unlockedAt) {
        return new AchievementProgress(
                current.playerId(),
                current.achievementId(),
                true,
                Optional.of(unlockedAt),
                definition.rewardId().map(ignored -> RewardClaimId.random()),
                current.revision());
    }

    private CompletionStage<Void> retryPendingUnlockEvent(
            UUID playerId, AchievementDefinition definition, AchievementProgress progress) {
        var key = new AchievementMutationQueue.Key(playerId, definition.id());
        if (!pendingUnlockEvents.contains(key)) {
            return completedVoid();
        }
        return publishUnlockEvent(playerId, definition, progress);
    }

    private CompletionStage<Void> publishUnlockEvent(
            UUID playerId, AchievementDefinition definition, AchievementProgress progress) {
        var key = new AchievementMutationQueue.Key(playerId, definition.id());
        pendingUnlockEvents.add(key);
        try {
            return options.withEventTimeout(eventBus.publishAsync(
                            new AchievementUnlockedEvent(playerId, definition.id(), definition.rewardId(), progress)))
                    .handle((ignored, failure) -> {
                        if (failure == null) {
                            pendingUnlockEvents.remove(key);
                        } else {
                            LOGGER.log(Level.WARNING, "Failed to publish achievement unlock event", unwrap(failure));
                        }
                        return null;
                    });
        } catch (RuntimeException failure) {
            LOGGER.log(Level.WARNING, "Failed to publish achievement unlock event", failure);
            return completedVoid();
        }
    }

    private void onStatisticChanged(StatisticChangedEvent event) {
        Set<AchievementId> achievementIds;
        synchronized (lifecycleLock) {
            if (closed.get()) {
                return;
            }
            var registeredIds = definitionsByStatistic.get(event.statisticId());
            if (registeredIds == null) {
                return;
            }
            achievementIds = Set.copyOf(registeredIds);
        }
        for (var achievementId : achievementIds) {
            evaluateAsync(event.playerId(), achievementId).whenComplete((ignored, failure) -> {
                if (failure != null) {
                    LOGGER.log(Level.WARNING, "Failed to evaluate achievement after statistic change", unwrap(failure));
                }
            });
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw closedFailure();
        }
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Achievement service is closed");
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

    private record SaveOutcome(
            @Nullable AchievementProgress saved, @Nullable Throwable failure) {}
}

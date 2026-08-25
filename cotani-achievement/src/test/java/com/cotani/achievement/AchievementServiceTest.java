package com.cotani.achievement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.achievement.api.AchievementCriterion;
import com.cotani.achievement.api.AchievementDefinition;
import com.cotani.achievement.api.AchievementId;
import com.cotani.achievement.api.AchievementNotUnlockedException;
import com.cotani.achievement.api.AchievementProgress;
import com.cotani.achievement.api.AchievementProgressConflictException;
import com.cotani.achievement.api.AchievementRepository;
import com.cotani.achievement.api.AchievementRewardNotConfiguredException;
import com.cotani.achievement.api.AchievementService;
import com.cotani.achievement.api.AchievementServiceOptions;
import com.cotani.achievement.api.event.AchievementUnlockedEvent;
import com.cotani.achievement.internal.InMemoryAchievementRepository;
import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventListener;
import com.cotani.event.subscription.EventSubscription;
import com.cotani.reward.api.RewardClaim;
import com.cotani.reward.api.RewardClaimId;
import com.cotani.reward.api.RewardId;
import com.cotani.reward.api.RewardService;
import com.cotani.statistics.api.StatisticEntry;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticService;
import com.cotani.statistics.api.event.StatisticChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AchievementServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AchievementId ACHIEVEMENT_ID = AchievementId.of("stone-master");
    private static final StatisticId STATISTIC_ID = StatisticId.of("blocks-mined");
    private static final StatisticId SECOND_STATISTIC_ID = StatisticId.of("blocks-placed");
    private static final AchievementId MULTI_CRITERIA_ID = AchievementId.of("builder");
    private static final RewardId REWARD_ID = RewardId.of("stone-reward");

    private final List<CotaniEvent> events = new ArrayList<>();
    private final AtomicBoolean failUnlockEvents = new AtomicBoolean();
    private StatisticService statistics;
    private RewardService rewards;
    private EventBus eventBus;
    private @Nullable EventSubscription statisticSubscription;
    private @Nullable EventListener<StatisticChangedEvent> statisticListener;
    private AchievementService service;

    @BeforeEach
    void setUp() {
        statistics = mock(StatisticService.class);
        rewards = mock(RewardService.class);
        eventBus = mock(EventBus.class);
        doAnswer(invocation -> {
                    statisticListener = invocation.getArgument(1);
                    statisticSubscription = mock(EventSubscription.class);
                    return statisticSubscription;
                })
                .when(eventBus)
                .subscribe(any(), any());
        doAnswer(invocation -> {
                    var event = invocation.<CotaniEvent>getArgument(0);
                    if (event instanceof AchievementUnlockedEvent && failUnlockEvents.get()) {
                        return CompletableFuture.failedFuture(new IllegalStateException("event bus unavailable"));
                    }
                    events.add(event);
                    return CompletableFuture.completedFuture(event);
                })
                .when(eventBus)
                .publishAsync(any());
        service = CotaniAchievements.inMemory(statistics, rewards, eventBus);
        service.register(definition());
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void doesNotUnlockWhenTheStatisticIsBelowTheThreshold() {
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(2))));

        var progress = service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                .toCompletableFuture()
                .join();

        assertFalse(progress.unlocked());
        assertTrue(events.stream().noneMatch(AchievementUnlockedEvent.class::isInstance));
    }

    @Test
    void unlocksOnceAndPersistsAStableRewardClaimId() {
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(3))));

        var first = service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                .toCompletableFuture()
                .join();
        var repeated = service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                .toCompletableFuture()
                .join();

        assertTrue(first.unlocked());
        assertEquals(first, repeated);
        assertTrue(first.rewardClaimId().isPresent());
        assertEquals(
                1,
                events.stream()
                        .filter(AchievementUnlockedEvent.class::isInstance)
                        .count());
    }

    @Test
    void evaluatesAutomaticallyAfterAStatisticChange() {
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(3))));

        Objects.requireNonNull(statisticListener, "statisticListener")
                .handle(new StatisticChangedEvent(PLAYER_ID, STATISTIC_ID, 1, 2, statistic(3)));

        var progress = service.findProgressAsync(PLAYER_ID, ACHIEVEMENT_ID)
                .toCompletableFuture()
                .join()
                .orElseThrow();
        assertTrue(progress.unlocked());
        assertEquals(
                1,
                events.stream()
                        .filter(AchievementUnlockedEvent.class::isInstance)
                        .count());
    }

    @Test
    void requiresEveryCriterionAndRejectsClaimsWithoutAConfiguredReward() {
        service.register(new AchievementDefinition(
                MULTI_CRITERIA_ID,
                List.of(new AchievementCriterion(STATISTIC_ID, 3), new AchievementCriterion(SECOND_STATISTIC_ID, 2)),
                Optional.empty()));
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(STATISTIC_ID, 3))));
        when(statistics.findAsync(PLAYER_ID, SECOND_STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(SECOND_STATISTIC_ID, 1))));

        assertFalse(service.evaluateAsync(PLAYER_ID, MULTI_CRITERIA_ID)
                .toCompletableFuture()
                .join()
                .unlocked());

        when(statistics.findAsync(PLAYER_ID, SECOND_STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(SECOND_STATISTIC_ID, 2))));
        assertTrue(service.evaluateAsync(PLAYER_ID, MULTI_CRITERIA_ID)
                .toCompletableFuture()
                .join()
                .unlocked());

        var failure = assertThrows(
                CompletionException.class,
                () -> service.claimRewardAsync(PLAYER_ID, MULTI_CRITERIA_ID)
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof AchievementRewardNotConfiguredException);
    }

    @Test
    void retriesAnUnlockEventAfterATransientPublicationFailure() {
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(3))));
        failUnlockEvents.set(true);

        service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID).toCompletableFuture().join();
        failUnlockEvents.set(false);
        service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID).toCompletableFuture().join();

        assertEquals(
                1,
                events.stream()
                        .filter(AchievementUnlockedEvent.class::isInstance)
                        .count());
    }

    @Test
    void claimsUsingThePersistedStableRewardClaimId() {
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(3))));
        var claim = mock(RewardClaim.class);
        when(rewards.claimAsync(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(claim));

        var progress = service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                .toCompletableFuture()
                .join();
        service.claimRewardAsync(PLAYER_ID, ACHIEVEMENT_ID)
                .toCompletableFuture()
                .join();

        verify(rewards)
                .claimAsync(PLAYER_ID, REWARD_ID, progress.rewardClaimId().orElseThrow());
    }

    @Test
    void rejectsClaimBeforeUnlock() {
        var failure = assertThrows(
                CompletionException.class,
                () -> service.claimRewardAsync(PLAYER_ID, ACHIEVEMENT_ID)
                        .toCompletableFuture()
                        .join());

        assertTrue(failure.getCause() instanceof AchievementNotUnlockedException);
    }

    @Test
    void closesTheStatisticSubscriptionAndRejectsNewMutations() {
        service.close();

        verify(Objects.requireNonNull(statisticSubscription, "statisticSubscription"))
                .unsubscribe();
        var failure = assertThrows(
                CompletionException.class,
                () -> service.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void rejectsMutationsWhenThePendingLimitIsReached() {
        var never = new CompletableFuture<CotaniEvent>();
        var limitedEventBus = mock(EventBus.class);
        doReturn(mock(EventSubscription.class)).when(limitedEventBus).subscribe(any(), any());
        doAnswer(invocation -> never).when(limitedEventBus).publishAsync(any());
        var limited = CotaniAchievements.fromRepository(
                new InMemoryAchievementRepository(),
                statistics,
                rewards,
                limitedEventBus,
                new AchievementServiceOptions(Duration.ofSeconds(1), 3, Duration.ofDays(1), 1));
        limited.register(definition());
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(3))));

        var first = limited.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID);
        var failure = assertThrows(
                CompletionException.class,
                () -> limited.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof java.util.concurrent.RejectedExecutionException);

        never.complete(new AchievementUnlockedEvent(
                PLAYER_ID,
                ACHIEVEMENT_ID,
                Optional.of(REWARD_ID),
                new AchievementProgress(
                        PLAYER_ID,
                        ACHIEVEMENT_ID,
                        true,
                        Optional.of(Instant.EPOCH),
                        Optional.of(RewardClaimId.random()),
                        1)));
        first.toCompletableFuture().join();
        limited.closeAsync().toCompletableFuture().join();
    }

    @Test
    void retriesRepositoryConflictsWithoutPublishingDuplicateUnlockEvents() {
        when(statistics.findAsync(PLAYER_ID, STATISTIC_ID))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(statistic(3))));
        var repository = new InMemoryAchievementRepository();
        var failures = new AtomicInteger(1);
        var conflictOnce = new AchievementRepository() {
            @Override
            public CompletionStage<Optional<AchievementProgress>> findAsync(
                    UUID playerId, AchievementId achievementId) {
                return repository.findAsync(playerId, achievementId);
            }

            @Override
            public CompletionStage<AchievementProgress> saveAsync(AchievementProgress progress, long expectedRevision) {
                if (failures.getAndDecrement() > 0) {
                    return CompletableFuture.failedFuture(new AchievementProgressConflictException(
                            AchievementProgress.initial(progress.playerId(), progress.achievementId()),
                            expectedRevision));
                }
                return repository.saveAsync(progress, expectedRevision);
            }
        };
        var retrying = CotaniAchievements.fromRepository(
                conflictOnce, statistics, rewards, eventBus, new AchievementServiceOptions(Duration.ofSeconds(1), 2));
        retrying.register(definition());

        try {
            assertTrue(retrying.evaluateAsync(PLAYER_ID, ACHIEVEMENT_ID)
                    .toCompletableFuture()
                    .join()
                    .unlocked());
            assertEquals(
                    1,
                    events.stream()
                            .filter(AchievementUnlockedEvent.class::isInstance)
                            .count());
        } finally {
            retrying.close();
        }
    }

    private static AchievementDefinition definition() {
        return new AchievementDefinition(
                ACHIEVEMENT_ID, List.of(new AchievementCriterion(STATISTIC_ID, 3)), Optional.of(REWARD_ID));
    }

    private static StatisticEntry statistic(long value) {
        return new StatisticEntry(PLAYER_ID, STATISTIC_ID, value, Instant.EPOCH, 1);
    }

    private static StatisticEntry statistic(StatisticId statisticId, long value) {
        return new StatisticEntry(PLAYER_ID, statisticId, value, Instant.EPOCH, 1);
    }
}

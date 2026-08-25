package com.cotani.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.quest.api.QuestAlreadyClaimedException;
import com.cotani.quest.api.QuestClaimId;
import com.cotani.quest.api.QuestDefinition;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestNotCompletedException;
import com.cotani.quest.api.QuestObjective;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.quest.api.QuestService;
import com.cotani.quest.api.QuestServiceOptions;
import com.cotani.quest.api.event.QuestClaimedEvent;
import com.cotani.quest.api.event.QuestCompletedEvent;
import com.cotani.quest.api.event.QuestProgressedEvent;
import com.cotani.reward.api.RewardId;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final QuestId QUEST_ID = QuestId.of("first-mining");
    private static final QuestObjectiveId OBJECTIVE_ID = QuestObjectiveId.of("mine-diamond");

    private final List<CotaniEvent> events = new ArrayList<>();
    private QuestService service;

    @BeforeEach
    void setUp() {
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(invocation.getArgument(0));
                })
                .when(eventBus)
                .publishAsync(any());
        service = CotaniQuests.inMemory(eventBus);
        service.register(definition());
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void recordsProgressAndPublishesCompletion() {
        var first = service.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 2)
                .toCompletableFuture()
                .join();
        assertFalse(first.completed());
        assertEquals(2, first.progressFor(OBJECTIVE_ID));

        var completed = service.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 5)
                .toCompletableFuture()
                .join();
        assertTrue(completed.completed());
        assertEquals(3, completed.progressFor(OBJECTIVE_ID));
        assertEquals(
                2,
                events.stream().filter(QuestProgressedEvent.class::isInstance).count());
        var progressEvents = events.stream()
                .filter(QuestProgressedEvent.class::isInstance)
                .map(QuestProgressedEvent.class::cast)
                .toList();
        assertEquals(
                List.of(2L, 1L),
                progressEvents.stream().map(QuestProgressedEvent::amount).toList());
        assertEquals(
                1, events.stream().filter(QuestCompletedEvent.class::isInstance).count());
    }

    @Test
    void rejectsClaimBeforeCompletion() {
        var failure = assertThrows(
                CompletionException.class,
                () -> service.claimAsync(PLAYER_ID, QUEST_ID)
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof QuestNotCompletedException);
    }

    @Test
    void claimIsIdempotentAndSharesRewardKey() {
        service.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 3)
                .toCompletableFuture()
                .join();
        var claimId = QuestClaimId.random();

        var first = service.claimAsync(PLAYER_ID, QUEST_ID, claimId)
                .toCompletableFuture()
                .join();
        var repeated = service.claimAsync(PLAYER_ID, QUEST_ID, claimId)
                .toCompletableFuture()
                .join();

        assertEquals(first, repeated);
        assertEquals(claimId.value(), first.rewardClaimId().value());
        assertEquals(
                1, events.stream().filter(QuestClaimedEvent.class::isInstance).count());
    }

    @Test
    void rejectsClaimWithDifferentKeyAfterCompletionWasClaimed() {
        service.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 3)
                .toCompletableFuture()
                .join();
        var existingClaimId = QuestClaimId.random();
        service.claimAsync(PLAYER_ID, QUEST_ID, existingClaimId)
                .toCompletableFuture()
                .join();

        var failure = assertThrows(
                CompletionException.class,
                () -> service.claimAsync(PLAYER_ID, QUEST_ID)
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof QuestAlreadyClaimedException);
        var alreadyClaimed = (QuestAlreadyClaimedException) failure.getCause();
        assertEquals(existingClaimId, alreadyClaimed.existingClaimId().orElseThrow());
    }

    @Test
    void rejectsEventsWithMismatchedProgress() {
        service.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 3)
                .toCompletableFuture()
                .join();
        var progress = service.findProgressAsync(PLAYER_ID, QUEST_ID)
                .toCompletableFuture()
                .join()
                .orElseThrow();

        assertThrows(
                IllegalArgumentException.class, () -> new QuestCompletedEvent(UUID.randomUUID(), QUEST_ID, progress));
    }

    @Test
    void eventTimeoutDoesNotFailDurableProgress() {
        var eventBus = mock(EventBus.class);
        CompletionStage<CotaniEvent> never = new CompletableFuture<>();
        doAnswer(invocation -> never).when(eventBus).publishAsync(any());
        var timedService = CotaniQuests.inMemory(
                eventBus, new QuestServiceOptions(Duration.ofSeconds(1), 3, Duration.ofMillis(10)));
        timedService.register(definition());
        try {
            var progress = timedService
                    .recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 1)
                    .toCompletableFuture()
                    .join();
            assertEquals(1, progress.progressFor(OBJECTIVE_ID));
        } finally {
            timedService.close();
        }
    }

    @Test
    void rejectsMutationsWhenThePendingLimitIsReached() {
        var eventBus = mock(EventBus.class);
        CompletionStage<CotaniEvent> never = new CompletableFuture<>();
        doAnswer(invocation -> never).when(eventBus).publishAsync(any());
        var limitedService = CotaniQuests.inMemory(
                eventBus, new QuestServiceOptions(Duration.ofSeconds(1), 3, Duration.ofMillis(10), 1));
        limitedService.register(definition());
        try {
            var first = limitedService.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 1);
            var failure = assertThrows(
                    CompletionException.class,
                    () -> limitedService
                            .recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 1)
                            .toCompletableFuture()
                            .join());
            assertTrue(failure.getCause() instanceof RejectedExecutionException);
            first.toCompletableFuture().join();
        } finally {
            limitedService.close();
        }
    }

    @Test
    void progressReadsWaitForAcceptedMutations() {
        service.recordProgressAsync(PLAYER_ID, QUEST_ID, OBJECTIVE_ID, 1)
                .toCompletableFuture()
                .join();

        var progress = service.findProgressAsync(PLAYER_ID, QUEST_ID)
                .toCompletableFuture()
                .join();
        assertTrue(progress.isPresent());
        assertEquals(1, progress.orElseThrow().progressFor(OBJECTIVE_ID));
    }

    private static QuestDefinition definition() {
        return new QuestDefinition(
                QUEST_ID,
                List.of(new QuestObjective(OBJECTIVE_ID, "mine", "diamond_ore", 3)),
                RewardId.of("mining-reward"));
    }
}

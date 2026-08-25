package com.cotani.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticOperationId;
import com.cotani.statistics.api.StatisticOverflowException;
import com.cotani.statistics.api.StatisticRankEntry;
import com.cotani.statistics.api.StatisticRepository;
import com.cotani.statistics.api.StatisticService;
import com.cotani.statistics.api.StatisticServiceOptions;
import com.cotani.statistics.api.StatisticUpdate;
import com.cotani.statistics.api.event.StatisticChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticServiceTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final StatisticId STATISTIC_ID = StatisticId.of("blocks-mined");

    private final List<CotaniEvent> events = new ArrayList<>();
    private StatisticService service;

    @BeforeEach
    void setUp() {
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(invocation.getArgument(0));
                })
                .when(eventBus)
                .publishAsync(any());
        service = CotaniStatistics.inMemory(eventBus);
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void incrementsAndPublishesExactChange() {
        var first = service.incrementAsync(PLAYER_ID, STATISTIC_ID, 3)
                .toCompletableFuture()
                .join();
        var second = service.incrementAsync(PLAYER_ID, STATISTIC_ID, 2)
                .toCompletableFuture()
                .join();

        assertEquals(3, first.value());
        assertEquals(5, second.value());
        var changes = events.stream()
                .filter(StatisticChangedEvent.class::isInstance)
                .map(StatisticChangedEvent.class::cast)
                .toList();
        assertEquals(
                List.of(3L, 2L),
                changes.stream().map(StatisticChangedEvent::amount).toList());
        assertEquals(
                List.of(0L, 3L),
                changes.stream().map(StatisticChangedEvent::previousValue).toList());
    }

    @Test
    void replaysAnIdempotentOperationWithoutDuplicatingTheIncrementOrEvent() {
        var operationId = StatisticOperationId.random();
        var first = service.incrementAsync(PLAYER_ID, STATISTIC_ID, 3, operationId)
                .toCompletableFuture()
                .join();
        var replay = service.incrementAsync(PLAYER_ID, STATISTIC_ID, 3, operationId)
                .toCompletableFuture()
                .join();

        assertEquals(first, replay);
        assertEquals(
                3,
                service.findAsync(PLAYER_ID, STATISTIC_ID)
                        .toCompletableFuture()
                        .join()
                        .orElseThrow()
                        .value());
        assertEquals(
                1,
                events.stream().filter(StatisticChangedEvent.class::isInstance).count());
    }

    @Test
    void retriesRepositoryConflictsWithinTheConfiguredLimit() {
        var delegate = new com.cotani.statistics.internal.InMemoryStatisticRepository();
        var firstAttempt = new AtomicBoolean(true);
        StatisticRepository conflictOnce = new StatisticRepository() {
            @Override
            public CompletionStage<Optional<com.cotani.statistics.api.StatisticEntry>> findAsync(
                    UUID playerId, StatisticId statisticId) {
                return delegate.findAsync(playerId, statisticId);
            }

            @Override
            public CompletionStage<StatisticUpdate> incrementAsync(
                    UUID playerId, StatisticId statisticId, long amount, Instant updatedAt) {
                if (firstAttempt.compareAndSet(true, false)) {
                    return CompletableFuture.failedFuture(
                            new com.cotani.statistics.api.StatisticConflictException(playerId, statisticId));
                }
                return delegate.incrementAsync(playerId, statisticId, amount, updatedAt);
            }

            @Override
            public CompletionStage<List<StatisticRankEntry>> topAsync(StatisticId statisticId, int limit) {
                return delegate.topAsync(statisticId, limit);
            }
        };
        var retrying = CotaniStatistics.fromRepository(
                conflictOnce, mock(EventBus.class), new StatisticServiceOptions(Duration.ofSeconds(1), 2));
        try {
            assertEquals(
                    4,
                    retrying.incrementAsync(PLAYER_ID, STATISTIC_ID, 4)
                            .toCompletableFuture()
                            .join()
                            .value());
        } finally {
            retrying.close();
        }
    }

    @Test
    void readsWaitForAcceptedMutations() {
        service.incrementAsync(PLAYER_ID, STATISTIC_ID, 7).toCompletableFuture().join();

        var entry = service.findAsync(PLAYER_ID, STATISTIC_ID)
                .toCompletableFuture()
                .join()
                .orElseThrow();
        assertEquals(7, entry.value());
        assertEquals(1, entry.revision());
    }

    @Test
    void ranksByValueAndUuidWithBoundedResults() {
        var first = UUID.fromString("00000000-0000-0000-0000-000000000002");
        var second = UUID.fromString("00000000-0000-0000-0000-000000000003");
        service.incrementAsync(first, STATISTIC_ID, 5).toCompletableFuture().join();
        service.incrementAsync(second, STATISTIC_ID, 5).toCompletableFuture().join();
        service.incrementAsync(PLAYER_ID, STATISTIC_ID, 9).toCompletableFuture().join();

        var ranking = service.topAsync(STATISTIC_ID, 2).toCompletableFuture().join();
        assertEquals(
                List.of(PLAYER_ID, first),
                ranking.entries().stream().map(entry -> entry.playerId()).toList());
        assertEquals(
                List.of(1, 2),
                ranking.entries().stream().map(entry -> entry.rank()).toList());
        assertEquals(
                List.of(9L, 5L),
                ranking.entries().stream().map(entry -> entry.value()).toList());
    }

    @Test
    void rejectsOverflowWithoutChangingTheStoredValue() {
        var repository = new com.cotani.statistics.internal.InMemoryStatisticRepository();
        repository
                .incrementAsync(PLAYER_ID, STATISTIC_ID, Long.MAX_VALUE, java.time.Instant.EPOCH)
                .toCompletableFuture()
                .join();

        var failure = assertThrows(
                CompletionException.class,
                () -> repository
                        .incrementAsync(PLAYER_ID, STATISTIC_ID, 1, java.time.Instant.EPOCH)
                        .toCompletableFuture()
                        .join());
        assertTrue(failure.getCause() instanceof StatisticOverflowException);
        assertEquals(
                Long.MAX_VALUE,
                repository
                        .findAsync(PLAYER_ID, STATISTIC_ID)
                        .toCompletableFuture()
                        .join()
                        .orElseThrow()
                        .value());
    }

    @Test
    void eventTimeoutDoesNotFailDurableIncrement() {
        var eventBus = mock(EventBus.class);
        CompletionStage<CotaniEvent> never = new CompletableFuture<>();
        doAnswer(invocation -> never).when(eventBus).publishAsync(any());
        var timed = CotaniStatistics.inMemory(
                eventBus, new StatisticServiceOptions(Duration.ofSeconds(1), 3, Duration.ofMillis(10)));
        try {
            assertEquals(
                    1,
                    timed.incrementAsync(PLAYER_ID, STATISTIC_ID, 1)
                            .toCompletableFuture()
                            .join()
                            .value());
        } finally {
            timed.close();
        }
    }

    @Test
    void mutationTimeoutReleasesTheServiceQueueWithoutBlockingClose() {
        var pending = new CompletableFuture<StatisticUpdate>();
        StatisticRepository hanging = new StatisticRepository() {
            @Override
            public CompletionStage<Optional<com.cotani.statistics.api.StatisticEntry>> findAsync(
                    UUID playerId, StatisticId statisticId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletionStage<StatisticUpdate> incrementAsync(
                    UUID playerId, StatisticId statisticId, long amount, Instant updatedAt) {
                return pending;
            }

            @Override
            public CompletionStage<List<StatisticRankEntry>> topAsync(StatisticId statisticId, int limit) {
                return CompletableFuture.completedFuture(List.of());
            }
        };
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> {
                    events.add(invocation.getArgument(0));
                    return CompletableFuture.completedFuture(invocation.getArgument(0));
                })
                .when(eventBus)
                .publishAsync(any());
        var timed = CotaniStatistics.fromRepository(
                hanging, eventBus, new StatisticServiceOptions(Duration.ofMillis(10), 1, Duration.ofMillis(10), 1));
        try {
            var failure = assertThrows(
                    CompletionException.class,
                    () -> timed.incrementAsync(PLAYER_ID, STATISTIC_ID, 1)
                            .toCompletableFuture()
                            .join());
            assertTrue(failure.getCause() instanceof TimeoutException);
            pending.complete(new StatisticUpdate(
                    1, 0, new com.cotani.statistics.api.StatisticEntry(PLAYER_ID, STATISTIC_ID, 1, Instant.EPOCH, 1)));
            assertEquals(
                    1,
                    events.stream()
                            .filter(StatisticChangedEvent.class::isInstance)
                            .count());
            timed.closeAsync().toCompletableFuture().join();
        } finally {
            timed.close();
        }
    }

    @Test
    void rejectsMutationsWhenPendingLimitIsReached() {
        var pending = new CompletableFuture<StatisticUpdate>();
        StatisticRepository repository = new StatisticRepository() {
            @Override
            public CompletionStage<Optional<com.cotani.statistics.api.StatisticEntry>> findAsync(
                    UUID playerId, StatisticId statisticId) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public CompletionStage<StatisticUpdate> incrementAsync(
                    UUID playerId, StatisticId statisticId, long amount, Instant updatedAt) {
                return pending;
            }

            @Override
            public CompletionStage<List<StatisticRankEntry>> topAsync(StatisticId statisticId, int limit) {
                return CompletableFuture.completedFuture(List.of());
            }
        };
        var eventBus = mock(EventBus.class);
        doAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0)))
                .when(eventBus)
                .publishAsync(any());
        var limited = CotaniStatistics.fromRepository(
                repository, eventBus, new StatisticServiceOptions(Duration.ofSeconds(1), 3, Duration.ofSeconds(1), 1));
        try {
            var first = limited.incrementAsync(PLAYER_ID, STATISTIC_ID, 1);
            var failure = assertThrows(
                    CompletionException.class,
                    () -> limited.incrementAsync(PLAYER_ID, STATISTIC_ID, 1)
                            .toCompletableFuture()
                            .join());
            assertTrue(failure.getCause() instanceof RejectedExecutionException);
            pending.complete(new StatisticUpdate(
                    1, 0, new com.cotani.statistics.api.StatisticEntry(PLAYER_ID, STATISTIC_ID, 1, Instant.EPOCH, 1)));
            first.toCompletableFuture().join();
        } finally {
            limited.close();
        }
    }
}

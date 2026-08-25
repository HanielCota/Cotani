package com.cotani.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.ranking.api.RankingDefinition;
import com.cotani.ranking.api.RankingEntry;
import com.cotani.ranking.api.RankingId;
import com.cotani.ranking.api.RankingNotFoundException;
import com.cotani.ranking.api.RankingService;
import com.cotani.ranking.api.RankingServiceOptions;
import com.cotani.ranking.api.RankingSnapshot;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticRankEntry;
import com.cotani.statistics.api.StatisticRanking;
import com.cotani.statistics.api.StatisticService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RankingServiceTest {
    private static final RankingId RANKING_ID = RankingId.of("blocks-mined");
    private static final StatisticId STATISTIC_ID = StatisticId.of("blocks-mined");
    private static final StatisticId OTHER_STATISTIC_ID = StatisticId.of("kills");
    private static final UUID FIRST_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private StatisticService statistics;
    private RankingService rankings;

    @BeforeEach
    void setUp() {
        statistics = mock(StatisticService.class);
        rankings = CotaniRankings.fromStatistics(statistics);
        rankings.register(new RankingDefinition(RANKING_ID, STATISTIC_ID, 10));
    }

    @AfterEach
    void tearDown() {
        rankings.close();
    }

    @Test
    void returnsAnImmutableDeterministicSnapshot() {
        when(statistics.topAsync(STATISTIC_ID, 2))
                .thenReturn(CompletableFuture.completedFuture(new StatisticRanking(
                        STATISTIC_ID,
                        List.of(
                                new StatisticRankEntry(1, FIRST_PLAYER, 9),
                                new StatisticRankEntry(2, SECOND_PLAYER, 7)))));

        var snapshot = rankings.topAsync(RANKING_ID, 2).toCompletableFuture().join();

        assertEquals(RANKING_ID, snapshot.definition().id());
        assertEquals(
                List.of(FIRST_PLAYER, SECOND_PLAYER),
                snapshot.entries().stream().map(entry -> entry.playerId()).toList());
        assertThrows(
                UnsupportedOperationException.class, () -> snapshot.entries().clear());
    }

    @Test
    void rejectsUnknownRankingsAndLimitsAboveDefinition() {
        var unknown = assertThrows(
                CompletionException.class,
                () -> rankings.topAsync(RankingId.of("unknown"), 1)
                        .toCompletableFuture()
                        .join());
        assertTrue(unknown.getCause() instanceof RankingNotFoundException);

        var invalidLimit = assertThrows(
                CompletionException.class,
                () -> rankings.topAsync(RANKING_ID, 11).toCompletableFuture().join());
        assertTrue(invalidLimit.getCause() instanceof IllegalArgumentException);
    }

    @Test
    void rejectsDuplicateRegistration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> rankings.register(new RankingDefinition(RANKING_ID, STATISTIC_ID, 10)));
    }

    @Test
    void rejectsOperationsAfterClose() {
        rankings.close();

        assertThrows(
                IllegalStateException.class,
                () -> rankings.register(new RankingDefinition(RankingId.of("kills"), OTHER_STATISTIC_ID, 10)));
        var failure = assertThrows(
                CompletionException.class,
                () -> rankings.topAsync(RANKING_ID, 1).toCompletableFuture().join());
        assertTrue(failure.getCause() instanceof RejectedExecutionException);
    }

    @Test
    void appliesVisibleTimeoutWithoutReleasingThePhysicalAdmission() {
        var pending = new CompletableFuture<StatisticRanking>();
        when(statistics.topAsync(STATISTIC_ID, 1)).thenReturn(pending);

        var timed = CotaniRankings.fromStatistics(statistics, new RankingServiceOptions(Duration.ofMillis(10), 1));
        timed.register(new RankingDefinition(RANKING_ID, STATISTIC_ID, 1));
        try {
            var failure = assertThrows(
                    CompletionException.class,
                    () -> timed.topAsync(RANKING_ID, 1).toCompletableFuture().join());
            assertTrue(failure.getCause() instanceof TimeoutException);
            var secondFailure = assertThrows(
                    CompletionException.class,
                    () -> timed.topAsync(RANKING_ID, 1).toCompletableFuture().join());
            assertTrue(secondFailure.getCause() instanceof RejectedExecutionException);
            var closeStage = timed.closeAsync();
            assertFalse(closeStage.toCompletableFuture().isDone());
            pending.complete(new StatisticRanking(STATISTIC_ID, List.of()));
            closeStage.toCompletableFuture().join();
        } finally {
            timed.close();
        }
    }

    @Test
    void rejectsADelegateResultForAnotherStatistic() {
        when(statistics.topAsync(STATISTIC_ID, 1))
                .thenReturn(CompletableFuture.completedFuture(new StatisticRanking(OTHER_STATISTIC_ID, List.of())));

        var failure = assertThrows(
                CompletionException.class,
                () -> rankings.topAsync(RANKING_ID, 1).toCompletableFuture().join());

        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void releasesAdmissionAfterSynchronousDelegateFailure() {
        var brokenStatistics = mock(StatisticService.class);
        var limited =
                CotaniRankings.fromStatistics(brokenStatistics, new RankingServiceOptions(Duration.ofSeconds(1), 1));
        limited.register(new RankingDefinition(RANKING_ID, STATISTIC_ID, 1));
        try {
            when(brokenStatistics.topAsync(STATISTIC_ID, 1))
                    .thenThrow(new IllegalStateException("delegate unavailable"));
            var failure = assertThrows(
                    CompletionException.class,
                    () -> limited.topAsync(RANKING_ID, 1).toCompletableFuture().join());
            assertTrue(failure.getCause() instanceof IllegalStateException);

            doReturn(CompletableFuture.completedFuture(new StatisticRanking(STATISTIC_ID, List.of())))
                    .when(brokenStatistics)
                    .topAsync(STATISTIC_ID, 1);
            assertTrue(limited.topAsync(RANKING_ID, 1)
                    .toCompletableFuture()
                    .join()
                    .entries()
                    .isEmpty());
        } finally {
            limited.close();
        }
    }

    @Test
    void releasesAdmissionAfterAsynchronousDelegateFailure() {
        var delegateFailure = new CompletableFuture<StatisticRanking>();
        var failingStatistics = mock(StatisticService.class);
        var limited =
                CotaniRankings.fromStatistics(failingStatistics, new RankingServiceOptions(Duration.ofSeconds(1), 1));
        limited.register(new RankingDefinition(RANKING_ID, STATISTIC_ID, 1));
        try {
            when(failingStatistics.topAsync(STATISTIC_ID, 1)).thenReturn(delegateFailure);
            var first = limited.topAsync(RANKING_ID, 1);
            delegateFailure.completeExceptionally(new IllegalStateException("delegate failed"));
            var failure = assertThrows(
                    CompletionException.class, () -> first.toCompletableFuture().join());
            assertTrue(failure.getCause() instanceof IllegalStateException);

            doReturn(CompletableFuture.completedFuture(new StatisticRanking(STATISTIC_ID, List.of())))
                    .when(failingStatistics)
                    .topAsync(STATISTIC_ID, 1);
            assertTrue(limited.topAsync(RANKING_ID, 1)
                    .toCompletableFuture()
                    .join()
                    .entries()
                    .isEmpty());
        } finally {
            limited.close();
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void rejectsNullSnapshotEntries() {
        var entries = new ArrayList<RankingEntry>();
        entries.addAll(Arrays.asList((RankingEntry) null));

        assertThrows(
                IllegalArgumentException.class,
                () -> new RankingSnapshot(new RankingDefinition(RANKING_ID, STATISTIC_ID, 1), entries, Instant.EPOCH));
    }
}

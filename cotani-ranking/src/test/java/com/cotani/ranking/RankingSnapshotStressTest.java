package com.cotani.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cotani.ranking.api.RankingDefinition;
import com.cotani.ranking.api.RankingId;
import com.cotani.statistics.api.StatisticId;
import com.cotani.statistics.api.StatisticRankEntry;
import com.cotani.statistics.api.StatisticRanking;
import com.cotani.statistics.api.StatisticService;
import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("stress")
class RankingSnapshotStressTest {
    @Test
    void repeatedThousandEntrySnapshotsPreserveRankValueAndPlayerOrdering() {
        var statisticId = StatisticId.of("generated-score");
        var rankingId = RankingId.of("generated-leaderboard");
        var entries = IntStream.range(0, 1_000)
                .mapToObj(index -> new StatisticRankEntry(index + 1, new UUID(20L, index + 1L), 1_000L - index))
                .toList();
        var statistics = mock(StatisticService.class);
        when(statistics.topAsync(statisticId, 1_000))
                .thenReturn(CompletableFuture.completedFuture(new StatisticRanking(statisticId, entries)));
        var service = CotaniRankings.fromStatistics(statistics);
        service.register(new RankingDefinition(rankingId, statisticId, 1_000));
        try {
            StressTestSupport.scenarios("ranking", "snapshot-ordering", (context, random, player) -> {
                var snapshot =
                        StressTestSupport.await(service.topAsync(rankingId, 1_000), Duration.ofSeconds(30), context);
                assertEquals(1_000, snapshot.entries().size(), context::description);
                int sample = context.iteration() % 1_000;
                var entry = snapshot.entries().get(sample);
                assertEquals(sample + 1, entry.rank(), context::description);
                assertEquals(new UUID(20L, sample + 1L), entry.playerId(), context::description);
                assertEquals(1_000L - sample, entry.value(), context::description);
            });
        } finally {
            service.closeAsync().toCompletableFuture().join();
        }
    }
}

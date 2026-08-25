package com.cotani.ranking;

import com.cotani.ranking.api.RankingService;
import com.cotani.ranking.api.RankingServiceOptions;
import com.cotani.ranking.internal.DefaultRankingService;
import com.cotani.statistics.api.StatisticService;
import java.time.Clock;
import java.util.Objects;

/** Factories for the {@code cotani-ranking} module. */
public final class CotaniRankings {
    private CotaniRankings() {}

    /** Creates a ranking service over a caller-owned statistics service. */
    public static RankingService fromStatistics(StatisticService statistics) {
        return fromStatistics(statistics, RankingServiceOptions.defaults());
    }

    /** Creates a ranking service with explicit operational bounds. */
    public static RankingService fromStatistics(StatisticService statistics, RankingServiceOptions options) {
        return fromStatistics(statistics, options, Clock.systemUTC());
    }

    /** Creates a ranking service with explicit operational bounds and clock. */
    public static RankingService fromStatistics(
            StatisticService statistics, RankingServiceOptions options, Clock clock) {
        return DefaultRankingService.create(
                Objects.requireNonNull(statistics, "statistics"),
                Objects.requireNonNull(options, "options"),
                Objects.requireNonNull(clock, "clock"));
    }
}

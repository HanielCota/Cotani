package com.cotani.ranking.api;

import com.cotani.statistics.api.StatisticId;
import java.util.Objects;

/** Immutable definition that maps a named ranking to one stored statistic. */
public record RankingDefinition(RankingId id, StatisticId statisticId, int maxEntries) {
    public RankingDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(statisticId, "statisticId");
        if (maxEntries <= 0 || maxEntries > 1_000) {
            throw new IllegalArgumentException("maxEntries must be between 1 and 1000");
        }
    }
}

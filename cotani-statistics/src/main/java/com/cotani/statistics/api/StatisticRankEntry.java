package com.cotani.statistics.api;

import java.util.Objects;
import java.util.UUID;

/** One deterministic position in a statistic ranking. */
public record StatisticRankEntry(int rank, UUID playerId, long value) {
    public StatisticRankEntry {
        Objects.requireNonNull(playerId, "playerId");
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive");
        }
        if (value < 0) {
            throw new IllegalArgumentException("value cannot be negative");
        }
    }
}

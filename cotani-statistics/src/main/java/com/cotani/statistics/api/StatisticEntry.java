package com.cotani.statistics.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable value and persistence metadata for one player statistic. */
public record StatisticEntry(UUID playerId, StatisticId statisticId, long value, Instant updatedAt, long revision) {
    public StatisticEntry {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (value < 0) {
            throw new IllegalArgumentException("value cannot be negative");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
    }

    public static StatisticEntry initial(UUID playerId, StatisticId statisticId) {
        return new StatisticEntry(playerId, statisticId, 0, Instant.EPOCH, 0);
    }
}

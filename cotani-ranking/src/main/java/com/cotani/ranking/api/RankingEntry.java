package com.cotani.ranking.api;

import java.util.Objects;
import java.util.UUID;

/** One deterministic position in a ranking snapshot. */
public record RankingEntry(int rank, UUID playerId, long value) {
    public RankingEntry {
        Objects.requireNonNull(playerId, "playerId");
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be positive");
        }
        if (value < 0) {
            throw new IllegalArgumentException("value cannot be negative");
        }
    }
}

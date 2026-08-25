package com.cotani.statistics.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable bounded ranking result for one statistic. */
public record StatisticRanking(StatisticId statisticId, List<StatisticRankEntry> entries) {
    public StatisticRanking {
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(entries, "entries");
        if (entries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("entries cannot contain null values");
        }
        if (entries.size() > 1_000) {
            throw new IllegalArgumentException("ranking cannot contain more than 1000 entries");
        }
        var players = new HashSet<UUID>();
        var expectedRank = 1;
        long previousValue = Long.MAX_VALUE;
        String previousPlayer = "";
        boolean hasPreviousEntry = false;
        for (var entry : entries) {
            if (!players.add(entry.playerId())) {
                throw new IllegalArgumentException("ranking cannot contain duplicate players");
            }
            if (entry.rank() != expectedRank) {
                throw new IllegalArgumentException("ranking ranks must start at one and be contiguous");
            }
            var player = entry.playerId().toString();
            if (entry.value() > previousValue
                    || (hasPreviousEntry && entry.value() == previousValue && player.compareTo(previousPlayer) < 0)) {
                throw new IllegalArgumentException("ranking entries must be value-descending and UUID-ascending");
            }
            previousValue = entry.value();
            previousPlayer = player;
            hasPreviousEntry = true;
            expectedRank++;
        }
        entries = List.copyOf(entries);
    }
}

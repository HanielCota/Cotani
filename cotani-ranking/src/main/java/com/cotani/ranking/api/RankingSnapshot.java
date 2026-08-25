package com.cotani.ranking.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, bounded view of one ranking at a point in time. */
public record RankingSnapshot(RankingDefinition definition, List<RankingEntry> entries, Instant generatedAt) {
    public RankingSnapshot {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(generatedAt, "generatedAt");
        if (entries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("entries cannot contain null values");
        }
        if (entries.size() > definition.maxEntries()) {
            throw new IllegalArgumentException("entries cannot exceed the ranking definition limit");
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

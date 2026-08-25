package com.cotani.season.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Immutable player progress snapshot for one season. */
public record SeasonProgress(
        UUID playerId, SeasonId seasonId, long experience, Set<Integer> claimedLevels, long revision) {
    public SeasonProgress {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(seasonId, "seasonId");
        Objects.requireNonNull(claimedLevels, "claimedLevels");
        if (experience < 0) {
            throw new IllegalArgumentException("experience cannot be negative");
        }
        if (claimedLevels.stream().anyMatch(level -> level == null || level <= 0)) {
            throw new IllegalArgumentException("claimed levels must be positive");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision cannot be negative");
        }
        claimedLevels = Set.copyOf(claimedLevels);
    }

    public static SeasonProgress initial(UUID playerId, SeasonId seasonId) {
        return new SeasonProgress(playerId, seasonId, 0, Set.of(), 0);
    }

    public boolean isClaimed(int level) {
        return claimedLevels.contains(level);
    }

    public SeasonProgress claimLevel(int level) {
        if (level <= 0) {
            throw new IllegalArgumentException("level must be positive");
        }
        var claimed = new java.util.HashSet<>(claimedLevels);
        claimed.add(level);
        return new SeasonProgress(playerId, seasonId, experience, claimed, revision);
    }

    public SeasonProgress withRevision(long newRevision) {
        return new SeasonProgress(playerId, seasonId, experience, claimedLevels, newRevision);
    }
}

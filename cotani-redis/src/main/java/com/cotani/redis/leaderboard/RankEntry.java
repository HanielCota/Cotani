package com.cotani.redis.leaderboard;

import java.util.Objects;

/**
 * Represents a member entry in a sorted set leaderboard with score and rank.
 *
 * @param member member identifier (e.g. player UUID or username)
 * @param score numeric score associated with the member
 * @param rank 1-based rank position in the leaderboard
 */
public record RankEntry(String member, double score, long rank) {

    public RankEntry {
        Objects.requireNonNull(member, "member");
        if (member.isBlank()) {
            throw new IllegalArgumentException("member must not be blank");
        }
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be at least 1");
        }
    }
}

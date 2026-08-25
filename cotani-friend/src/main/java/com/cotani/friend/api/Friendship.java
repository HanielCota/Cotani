package com.cotani.friend.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable bidirectional friendship stored in canonical player order. */
public record Friendship(UUID firstPlayerId, UUID secondPlayerId, Instant createdAt) {
    public Friendship {
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        Objects.requireNonNull(createdAt, "createdAt");
        if (firstPlayerId.equals(secondPlayerId)) {
            throw new IllegalArgumentException("A player cannot be friends with themselves");
        }
        if (firstPlayerId.compareTo(secondPlayerId) >= 0) {
            throw new IllegalArgumentException("Friendship players must be in canonical order");
        }
    }

    public static Friendship create(UUID firstPlayerId, UUID secondPlayerId, Instant createdAt) {
        Objects.requireNonNull(firstPlayerId, "firstPlayerId");
        Objects.requireNonNull(secondPlayerId, "secondPlayerId");
        var first = firstPlayerId.compareTo(secondPlayerId) < 0 ? firstPlayerId : secondPlayerId;
        var second = first.equals(firstPlayerId) ? secondPlayerId : firstPlayerId;
        return new Friendship(first, second, createdAt);
    }

    public boolean contains(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return firstPlayerId.equals(playerId) || secondPlayerId.equals(playerId);
    }

    public UUID friendOf(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (firstPlayerId.equals(playerId)) {
            return secondPlayerId;
        }
        if (secondPlayerId.equals(playerId)) {
            return firstPlayerId;
        }
        throw new IllegalArgumentException("Player is not part of this friendship");
    }
}

package com.cotani.friend.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable directed request from one player to another. */
public record FriendRequest(UUID requesterId, UUID targetId, Instant requestedAt) {
    public FriendRequest {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("A player cannot request themselves");
        }
    }
}

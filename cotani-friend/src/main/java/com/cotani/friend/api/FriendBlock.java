package com.cotani.friend.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable directed block from one player to another. */
public record FriendBlock(UUID blockerId, UUID blockedId, Instant blockedAt) {
    public FriendBlock {
        Objects.requireNonNull(blockerId, "blockerId");
        Objects.requireNonNull(blockedId, "blockedId");
        Objects.requireNonNull(blockedAt, "blockedAt");
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("A player cannot block themselves");
        }
    }
}

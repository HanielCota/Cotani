package com.cotani.friend.api.event;

import java.util.Objects;
import java.util.UUID;

/** Published after a requester cancels a pending friend request. */
public record FriendRequestCancelledEvent(UUID requesterId, UUID targetId) implements FriendEvent {
    public FriendRequestCancelledEvent {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("requesterId and targetId must differ");
        }
    }
}

package com.cotani.friend.api.event;

import java.util.Objects;
import java.util.UUID;

/** Published after a pending friend request is declined. */
public record FriendRequestDeclinedEvent(UUID requesterId, UUID targetId, UUID declinedBy) implements FriendEvent {
    public FriendRequestDeclinedEvent {
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(declinedBy, "declinedBy");
        if (requesterId.equals(targetId)) {
            throw new IllegalArgumentException("requesterId and targetId must differ");
        }
        if (!declinedBy.equals(requesterId) && !declinedBy.equals(targetId)) {
            throw new IllegalArgumentException("declinedBy must be part of the request");
        }
    }
}

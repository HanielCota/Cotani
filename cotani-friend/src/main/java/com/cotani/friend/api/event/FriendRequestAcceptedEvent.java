package com.cotani.friend.api.event;

import com.cotani.friend.api.Friendship;
import java.util.Objects;
import java.util.UUID;

/** Published after a friend request becomes a friendship. */
public record FriendRequestAcceptedEvent(Friendship friendship, UUID requesterId, UUID acceptedBy)
        implements FriendEvent {
    public FriendRequestAcceptedEvent {
        Objects.requireNonNull(friendship, "friendship");
        Objects.requireNonNull(requesterId, "requesterId");
        Objects.requireNonNull(acceptedBy, "acceptedBy");
        if (requesterId.equals(acceptedBy)) {
            throw new IllegalArgumentException("requesterId and acceptedBy must differ");
        }
        if (!friendship.contains(requesterId) || !friendship.contains(acceptedBy)) {
            throw new IllegalArgumentException("request participants must be part of the friendship");
        }
    }
}

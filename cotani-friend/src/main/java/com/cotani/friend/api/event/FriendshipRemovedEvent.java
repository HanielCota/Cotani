package com.cotani.friend.api.event;

import com.cotani.friend.api.Friendship;
import java.util.Objects;
import java.util.UUID;

/** Published after a friendship is removed. */
public record FriendshipRemovedEvent(Friendship friendship, UUID removedBy) implements FriendEvent {
    public FriendshipRemovedEvent {
        Objects.requireNonNull(friendship, "friendship");
        Objects.requireNonNull(removedBy, "removedBy");
        if (!friendship.contains(removedBy)) {
            throw new IllegalArgumentException("removedBy must be part of the friendship");
        }
    }
}

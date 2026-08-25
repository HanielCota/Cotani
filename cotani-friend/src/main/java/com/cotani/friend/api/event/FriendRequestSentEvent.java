package com.cotani.friend.api.event;

import com.cotani.friend.api.FriendRequest;
import java.util.Objects;

/** Published after a friend request is committed. */
public record FriendRequestSentEvent(FriendRequest request) implements FriendEvent {
    public FriendRequestSentEvent {
        Objects.requireNonNull(request, "request");
    }
}

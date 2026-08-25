package com.cotani.friend.api.event;

import com.cotani.friend.api.FriendBlock;
import java.util.Objects;

/** Published after a player blocks another player. */
public record FriendBlockedEvent(FriendBlock block) implements FriendEvent {
    public FriendBlockedEvent {
        Objects.requireNonNull(block, "block");
    }
}

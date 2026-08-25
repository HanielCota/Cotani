package com.cotani.friend.api.event;

import com.cotani.friend.api.FriendBlock;
import java.util.Objects;

/** Published after a player removes one of their blocks. */
public record FriendUnblockedEvent(FriendBlock block) implements FriendEvent {
    public FriendUnblockedEvent {
        Objects.requireNonNull(block, "block");
    }
}

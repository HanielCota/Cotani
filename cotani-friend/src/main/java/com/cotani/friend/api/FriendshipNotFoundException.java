package com.cotani.friend.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a friendship is required but does not exist. */
public final class FriendshipNotFoundException extends FriendException {
    private static final long serialVersionUID = 1L;
    private final UUID firstPlayerId;
    private final UUID secondPlayerId;

    public FriendshipNotFoundException(UUID firstPlayerId, UUID secondPlayerId) {
        super("Friendship not found between "
                + Objects.requireNonNull(firstPlayerId, "firstPlayerId")
                + " and "
                + Objects.requireNonNull(secondPlayerId, "secondPlayerId"));
        this.firstPlayerId = firstPlayerId;
        this.secondPlayerId = secondPlayerId;
    }

    public UUID firstPlayerId() {
        return firstPlayerId;
    }

    public UUID secondPlayerId() {
        return secondPlayerId;
    }
}

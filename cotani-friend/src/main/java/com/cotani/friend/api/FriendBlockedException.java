package com.cotani.friend.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a friendship operation is prevented by a block. */
public final class FriendBlockedException extends FriendException {
    private static final long serialVersionUID = 1L;
    private final UUID firstPlayerId;
    private final UUID secondPlayerId;

    public FriendBlockedException(UUID firstPlayerId, UUID secondPlayerId) {
        super("Friendship is blocked between "
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

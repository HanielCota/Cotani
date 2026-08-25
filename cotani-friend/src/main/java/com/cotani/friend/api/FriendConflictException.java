package com.cotani.friend.api;

/** Raised when a friendship or request conflicts with the current state. */
public final class FriendConflictException extends FriendException {
    private static final long serialVersionUID = 1L;

    public FriendConflictException(String message) {
        super(message);
    }
}

package com.cotani.friend.api;

/** Raised when a requested friend-request transition is invalid. */
public final class FriendRequestException extends FriendException {
    private static final long serialVersionUID = 1L;

    public FriendRequestException(String message) {
        super(message);
    }
}

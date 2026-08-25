package com.cotani.friend.api;

import java.util.Objects;

/** Base exception for expected friendship-domain failures. */
public class FriendException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public FriendException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}

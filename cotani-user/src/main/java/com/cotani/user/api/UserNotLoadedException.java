package com.cotani.user.api;

import java.io.Serial;
import java.util.UUID;

public final class UserNotLoadedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UserNotLoadedException(UUID uniqueId) {
        super("User is not loaded: " + uniqueId);
    }
}

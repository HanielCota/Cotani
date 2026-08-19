package com.cotani.user.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserNotLoadedExceptionTest {
    @Test
    void messageContainsUniqueId() {
        UUID uniqueId = UUID.randomUUID();

        UserNotLoadedException exception = new UserNotLoadedException(uniqueId);

        assertEquals("User is not loaded: " + uniqueId, exception.getMessage());
    }

    @Test
    void isARuntimeException() {
        UserNotLoadedException exception = new UserNotLoadedException(UUID.randomUUID());

        assertEquals(RuntimeException.class, exception.getClass().getSuperclass());
    }
}

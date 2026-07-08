package com.cotani.event.example;

import com.cotani.event.api.CotaniEvent;
import java.util.Objects;
import java.util.UUID;

public record UserLoadedEvent(UUID userId) implements CotaniEvent {

    public UserLoadedEvent {
        Objects.requireNonNull(userId, "userId cannot be null");
    }
}

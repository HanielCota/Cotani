package com.cotani.cooldown.api;

import java.util.Objects;
import java.util.UUID;

public record UserCooldownTarget(UUID userId) implements CooldownTarget {

    public UserCooldownTarget {
        Objects.requireNonNull(userId, "userId cannot be null");
    }
}

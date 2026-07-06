package com.cotani.cooldown.api;

import java.util.Objects;

public record ResourceCooldownTarget(String resourceId) implements CooldownTarget {

    public ResourceCooldownTarget {
        Objects.requireNonNull(resourceId, "resourceId cannot be null");

        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId cannot be blank");
        }
    }
}

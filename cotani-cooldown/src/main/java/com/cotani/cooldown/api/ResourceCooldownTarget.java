package com.cotani.cooldown.api;

import java.util.Objects;

public record ResourceCooldownTarget(String resourceId) implements CooldownTarget {
    private static final int MAXIMUM_LENGTH = 128;

    public ResourceCooldownTarget {
        Objects.requireNonNull(resourceId, "resourceId cannot be null");

        if (resourceId.isBlank()) {
            throw new IllegalArgumentException("resourceId cannot be blank");
        }
        if (resourceId.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("resourceId exceeds maximum length " + MAXIMUM_LENGTH);
        }
    }
}

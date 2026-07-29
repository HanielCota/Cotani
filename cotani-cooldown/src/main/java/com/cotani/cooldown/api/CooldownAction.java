package com.cotani.cooldown.api;

import java.util.Objects;

public record CooldownAction(String value) {

    private static final int MAXIMUM_LENGTH = 64;

    public CooldownAction {
        Objects.requireNonNull(value, "value cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        if (value.length() > MAXIMUM_LENGTH) {
            throw new IllegalArgumentException("value exceeds maximum length " + MAXIMUM_LENGTH);
        }
    }

    public static CooldownAction of(String value) {
        return new CooldownAction(value);
    }
}

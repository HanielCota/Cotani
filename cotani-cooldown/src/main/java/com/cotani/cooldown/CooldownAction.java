package com.cotani.cooldown;

import java.util.Objects;

public record CooldownAction(String value) {

    public CooldownAction {
        Objects.requireNonNull(value, "value cannot be null");

        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
    }

    public static CooldownAction of(String value) {
        return new CooldownAction(value);
    }
}

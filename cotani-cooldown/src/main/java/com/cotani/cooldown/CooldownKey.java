package com.cotani.cooldown;

import java.util.Objects;

public record CooldownKey(CooldownTarget target, CooldownAction action) {

    public CooldownKey {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(action, "action cannot be null");
    }
}

package com.cotani.reward.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for a registered reward definition. */
public record RewardId(String value) {
    public RewardId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Reward id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static RewardId of(String value) {
        return new RewardId(value);
    }
}

package com.cotani.season.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for one season. */
public record SeasonId(String value) {
    public SeasonId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Season id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static SeasonId of(String value) {
        return new SeasonId(value);
    }
}

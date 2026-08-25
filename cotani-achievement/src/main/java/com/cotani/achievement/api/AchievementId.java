package com.cotani.achievement.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for one achievement definition. */
public record AchievementId(String value) {
    public AchievementId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Achievement id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static AchievementId of(String value) {
        return new AchievementId(value);
    }
}

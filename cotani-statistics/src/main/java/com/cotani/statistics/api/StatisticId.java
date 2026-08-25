package com.cotani.statistics.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for one player statistic. */
public record StatisticId(String value) {
    public StatisticId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Statistic id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static StatisticId of(String value) {
        return new StatisticId(value);
    }
}

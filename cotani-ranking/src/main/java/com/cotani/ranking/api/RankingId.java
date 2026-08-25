package com.cotani.ranking.api;

import java.util.Locale;
import java.util.Objects;

/** Stable identifier for one named ranking. */
public record RankingId(String value) {
    public RankingId {
        Objects.requireNonNull(value, "value");
        value = value.strip().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Ranking id must match [a-z0-9][a-z0-9._-]{0,63}");
        }
    }

    public static RankingId of(String value) {
        return new RankingId(value);
    }
}

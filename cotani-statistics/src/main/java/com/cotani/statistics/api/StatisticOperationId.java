package com.cotani.statistics.api;

import java.util.Objects;
import java.util.UUID;

/** Stable idempotency key for one logical statistic increment. */
public record StatisticOperationId(UUID value) {
    public StatisticOperationId {
        Objects.requireNonNull(value, "value");
    }

    public static StatisticOperationId random() {
        return new StatisticOperationId(UUID.randomUUID());
    }

    public static StatisticOperationId of(UUID value) {
        return new StatisticOperationId(value);
    }
}

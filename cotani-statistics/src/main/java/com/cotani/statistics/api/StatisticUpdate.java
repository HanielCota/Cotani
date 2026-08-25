package com.cotani.statistics.api;

import java.util.Objects;

/** Atomic result of one positive statistic increment. */
public record StatisticUpdate(long amount, long previousValue, StatisticEntry current, boolean newlyApplied) {
    public StatisticUpdate(long amount, long previousValue, StatisticEntry current) {
        this(amount, previousValue, current, true);
    }

    public StatisticUpdate {
        Objects.requireNonNull(current, "current");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (previousValue < 0 || current.value() < previousValue) {
            throw new IllegalArgumentException("statistic values cannot decrease");
        }
        if (current.value() - previousValue != amount) {
            throw new IllegalArgumentException("current value must equal previous value plus amount");
        }
    }
}

package com.cotani.statistics.api.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.statistics.api.StatisticEntry;
import com.cotani.statistics.api.StatisticId;
import java.util.Objects;
import java.util.UUID;

/** Published after an atomic statistic increment is durably accepted. */
public record StatisticChangedEvent(
        UUID playerId, StatisticId statisticId, long amount, long previousValue, StatisticEntry current)
        implements CotaniEvent {
    public StatisticChangedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(statisticId, "statisticId");
        Objects.requireNonNull(current, "current");
        if (amount <= 0 || previousValue < 0) {
            throw new IllegalArgumentException("amount must be positive and previousValue cannot be negative");
        }
        if (!current.playerId().equals(playerId) || !current.statisticId().equals(statisticId)) {
            throw new IllegalArgumentException("current must belong to the event player and statistic");
        }
        if (current.value() - previousValue != amount) {
            throw new IllegalArgumentException("current value must equal previous value plus amount");
        }
    }
}

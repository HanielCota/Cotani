package com.cotani.statistics.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when an atomic statistic mutation lost an initialization race. */
public final class StatisticConflictException extends StatisticException {
    private static final long serialVersionUID = 1L;
    private final transient UUID playerId;
    private final transient StatisticId statisticId;

    public StatisticConflictException(UUID playerId, StatisticId statisticId) {
        super("Statistic mutation conflicted for player "
                + Objects.requireNonNull(playerId, "playerId")
                + ": "
                + Objects.requireNonNull(statisticId, "statisticId").value());
        this.playerId = playerId;
        this.statisticId = statisticId;
    }

    public UUID playerId() {
        return playerId;
    }

    public StatisticId statisticId() {
        return statisticId;
    }
}

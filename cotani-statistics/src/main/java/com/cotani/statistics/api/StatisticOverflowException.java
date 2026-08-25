package com.cotani.statistics.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when an increment would exceed {@link Long#MAX_VALUE}. */
public final class StatisticOverflowException extends StatisticException {
    private static final long serialVersionUID = 1L;
    private final transient UUID playerId;
    private final transient StatisticId statisticId;

    public StatisticOverflowException(UUID playerId, StatisticId statisticId) {
        super("Statistic value overflow for player "
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

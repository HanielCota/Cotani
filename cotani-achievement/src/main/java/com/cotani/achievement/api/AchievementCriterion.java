package com.cotani.achievement.api;

import com.cotani.statistics.api.StatisticId;
import java.util.Objects;

/** Immutable threshold criterion backed by one player statistic. */
public record AchievementCriterion(StatisticId statisticId, long requiredValue) {
    public AchievementCriterion {
        Objects.requireNonNull(statisticId, "statisticId");
        if (requiredValue <= 0) {
            throw new IllegalArgumentException("requiredValue must be positive");
        }
    }
}

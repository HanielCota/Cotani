package com.cotani.achievement.api;

import com.cotani.reward.api.RewardId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable achievement definition composed of statistic thresholds and an optional reward. */
public record AchievementDefinition(
        AchievementId id, List<AchievementCriterion> criteria, Optional<RewardId> rewardId) {
    public AchievementDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(rewardId, "rewardId");
        if (criteria.isEmpty() || criteria.size() > 64 || criteria.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("criteria must contain between 1 and 64 non-null values");
        }
        var statisticIds = new HashSet<>();
        for (var criterion : criteria) {
            if (!statisticIds.add(criterion.statisticId())) {
                throw new IllegalArgumentException("Duplicate achievement statistic: "
                        + criterion.statisticId().value());
            }
        }
        criteria = List.copyOf(criteria);
    }
}

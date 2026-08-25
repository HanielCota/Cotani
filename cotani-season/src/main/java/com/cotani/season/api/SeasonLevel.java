package com.cotani.season.api;

import com.cotani.reward.api.RewardId;
import java.util.Objects;

/** Immutable cumulative experience threshold and reward for one season level. */
public record SeasonLevel(int level, long requiredExperience, RewardId rewardId) {
    public SeasonLevel {
        Objects.requireNonNull(rewardId, "rewardId");
        if (level <= 0) {
            throw new IllegalArgumentException("level must be positive");
        }
        if (requiredExperience < 0) {
            throw new IllegalArgumentException("requiredExperience cannot be negative");
        }
    }
}

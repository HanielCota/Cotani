package com.cotani.achievement.api;

import java.util.Objects;

/** Raised when an achievement has no reward configured for a reward claim request. */
public final class AchievementRewardNotConfiguredException extends AchievementException {
    private static final long serialVersionUID = 1L;
    private final transient AchievementId achievementId;

    public AchievementRewardNotConfiguredException(AchievementId achievementId) {
        super("Achievement has no reward configured: "
                + Objects.requireNonNull(achievementId, "achievementId").value());
        this.achievementId = achievementId;
    }

    public AchievementId achievementId() {
        return achievementId;
    }
}

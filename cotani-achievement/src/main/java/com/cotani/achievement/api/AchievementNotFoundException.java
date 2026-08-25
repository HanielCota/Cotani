package com.cotani.achievement.api;

import java.util.Objects;

/** Raised when an achievement id is not registered. */
public final class AchievementNotFoundException extends AchievementException {
    private static final long serialVersionUID = 1L;
    private final transient AchievementId achievementId;

    public AchievementNotFoundException(AchievementId achievementId) {
        super("Achievement is not registered: "
                + Objects.requireNonNull(achievementId, "achievementId").value());
        this.achievementId = achievementId;
    }

    public AchievementId achievementId() {
        return achievementId;
    }
}

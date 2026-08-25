package com.cotani.achievement.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a player attempts to claim an achievement reward before unlocking it. */
public final class AchievementNotUnlockedException extends AchievementException {
    private static final long serialVersionUID = 1L;
    private final transient UUID playerId;
    private final transient AchievementId achievementId;

    public AchievementNotUnlockedException(UUID playerId, AchievementId achievementId) {
        super("Achievement is not unlocked for player: "
                + Objects.requireNonNull(playerId, "playerId") + " / "
                + Objects.requireNonNull(achievementId, "achievementId").value());
        this.playerId = playerId;
        this.achievementId = achievementId;
    }

    public UUID playerId() {
        return playerId;
    }

    public AchievementId achievementId() {
        return achievementId;
    }
}

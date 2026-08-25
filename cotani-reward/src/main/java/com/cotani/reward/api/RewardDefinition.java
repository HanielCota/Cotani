package com.cotani.reward.api;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Immutable definition containing cooldown, streak rules and the grants for a reward. */
public record RewardDefinition(
        RewardId id, Duration cooldown, Duration streakWindow, int maxStreak, List<RewardGrant> grants) {
    public RewardDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cooldown, "cooldown");
        Objects.requireNonNull(streakWindow, "streakWindow");
        Objects.requireNonNull(grants, "grants");
        if (cooldown.isZero() || cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must be positive");
        }
        if (streakWindow.isZero() || streakWindow.isNegative()) {
            throw new IllegalArgumentException("streakWindow must be positive");
        }
        if (streakWindow.compareTo(cooldown) < 0) {
            throw new IllegalArgumentException("streakWindow must be greater than or equal to cooldown");
        }
        if (maxStreak <= 0) {
            throw new IllegalArgumentException("maxStreak must be positive");
        }
        if (grants.isEmpty() || grants.size() > 32 || grants.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("grants must contain between 1 and 32 non-null values");
        }
        grants = List.copyOf(grants);
    }
}

package com.cotani.achievement.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Bukkit-free optimistic persistence SPI for per-player achievement state. */
public interface AchievementRepository {
    /** Loads one progress snapshot; absence means the player has not unlocked the achievement. */
    CompletionStage<Optional<AchievementProgress>> findAsync(UUID playerId, AchievementId achievementId);

    /** Saves a snapshot only when its stored revision equals the expected revision. */
    CompletionStage<AchievementProgress> saveAsync(AchievementProgress progress, long expectedRevision);
}

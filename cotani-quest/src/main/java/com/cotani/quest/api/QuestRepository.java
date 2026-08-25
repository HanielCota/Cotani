package com.cotani.quest.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Bukkit-free optimistic persistence SPI for quest progress. */
public interface QuestRepository {
    /** Loads one snapshot; absence means the player has not started the quest. */
    CompletionStage<Optional<QuestProgress>> findAsync(UUID playerId, QuestId questId);

    /**
     * Saves a snapshot only when its stored revision equals {@code expectedRevision}.
     * Implementations must return the snapshot with its newly assigned revision.
     */
    CompletionStage<QuestProgress> saveAsync(QuestProgress progress, long expectedRevision);
}

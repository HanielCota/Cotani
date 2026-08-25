package com.cotani.quest.api.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestProgress;
import java.util.Objects;
import java.util.UUID;

/** Published once when a player's progress first satisfies every objective. */
public record QuestCompletedEvent(UUID playerId, QuestId questId, QuestProgress progress) implements CotaniEvent {
    public QuestCompletedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(progress, "progress");
        if (!progress.completed()) {
            throw new IllegalArgumentException("progress must be completed");
        }
        if (!progress.playerId().equals(playerId) || !progress.questId().equals(questId)) {
            throw new IllegalArgumentException("progress must belong to the event player and quest");
        }
    }
}

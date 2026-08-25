package com.cotani.quest.api.event;

import com.cotani.event.api.CotaniEvent;
import com.cotani.quest.api.QuestId;
import com.cotani.quest.api.QuestObjectiveId;
import com.cotani.quest.api.QuestProgress;
import java.util.Objects;
import java.util.UUID;

/** Published after progress has been durably accepted. */
public record QuestProgressedEvent(
        UUID playerId, QuestId questId, QuestObjectiveId objectiveId, long amount, QuestProgress progress)
        implements CotaniEvent {
    public QuestProgressedEvent {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(objectiveId, "objectiveId");
        Objects.requireNonNull(progress, "progress");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (!progress.playerId().equals(playerId) || !progress.questId().equals(questId)) {
            throw new IllegalArgumentException("progress must belong to the event player and quest");
        }
        if (!progress.objectiveProgress().containsKey(objectiveId)) {
            throw new IllegalArgumentException("progress must contain the progressed objective");
        }
    }
}

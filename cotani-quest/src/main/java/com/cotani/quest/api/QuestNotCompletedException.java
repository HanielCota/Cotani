package com.cotani.quest.api;

import java.util.Objects;
import java.util.UUID;

/** Raised when a player attempts to claim an incomplete quest. */
public final class QuestNotCompletedException extends QuestException {
    private static final long serialVersionUID = 1L;
    private final transient UUID playerId;
    private final transient QuestId questId;

    public QuestNotCompletedException(UUID playerId, QuestId questId) {
        super("Quest is not completed for player "
                + Objects.requireNonNull(playerId, "playerId")
                + ": "
                + Objects.requireNonNull(questId, "questId").value());
        this.playerId = playerId;
        this.questId = questId;
    }

    public UUID playerId() {
        return playerId;
    }

    public QuestId questId() {
        return questId;
    }
}

package com.cotani.quest.api;

import java.util.Objects;

/** Raised when a quest id is not registered. */
public final class QuestNotFoundException extends QuestException {
    private static final long serialVersionUID = 1L;
    private final transient QuestId questId;

    public QuestNotFoundException(QuestId questId) {
        super("Quest is not registered: "
                + Objects.requireNonNull(questId, "questId").value());
        this.questId = questId;
    }

    public QuestId questId() {
        return questId;
    }
}

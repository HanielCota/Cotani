package com.cotani.quest.api;

import java.util.Objects;

/** Raised when another writer changed a quest snapshot before it could be saved. */
public final class QuestProgressConflictException extends QuestException {
    private static final long serialVersionUID = 1L;
    private final transient QuestProgress progress;
    private final long expectedRevision;

    public QuestProgressConflictException(QuestProgress progress, long expectedRevision) {
        super(message(Objects.requireNonNull(progress, "progress"), expectedRevision));
        this.progress = progress;
        this.expectedRevision = expectedRevision;
    }

    public QuestProgress progress() {
        return progress;
    }

    public long expectedRevision() {
        return expectedRevision;
    }

    public long actualRevision() {
        return progress.revision();
    }

    private static String message(QuestProgress progress, long expectedRevision) {
        return "Quest progress revision conflict for player "
                + progress.playerId()
                + ", quest "
                + progress.questId().value()
                + ": expected "
                + expectedRevision
                + ", actual "
                + progress.revision();
    }
}

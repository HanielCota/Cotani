package com.cotani.achievement.api;

import java.util.Objects;

/** Raised when an optimistic achievement progress write observes a newer revision. */
public final class AchievementProgressConflictException extends AchievementException {
    private static final long serialVersionUID = 1L;
    private final transient AchievementProgress current;
    private final long expectedRevision;

    public AchievementProgressConflictException(AchievementProgress current, long expectedRevision) {
        super("Achievement progress revision conflict: expected " + expectedRevision + ", actual "
                + Objects.requireNonNull(current, "current").revision());
        if (expectedRevision < 0) {
            throw new IllegalArgumentException("expectedRevision cannot be negative");
        }
        this.current = current;
        this.expectedRevision = expectedRevision;
    }

    public AchievementProgress current() {
        return current;
    }

    public long expectedRevision() {
        return expectedRevision;
    }
}

package com.cotani.season.api;

/** Raised when optimistic season progress persistence detects a concurrent writer. */
public final class SeasonProgressConflictException extends SeasonException {
    private static final long serialVersionUID = 1L;
    private final transient SeasonProgress actual;
    private final long expectedRevision;

    public SeasonProgressConflictException(SeasonProgress actual, long expectedRevision) {
        super("Season progress revision conflict: expected " + expectedRevision + ", actual " + actual.revision());
        this.actual = actual;
        this.expectedRevision = expectedRevision;
    }

    public SeasonProgress actual() {
        return actual;
    }

    public long expectedRevision() {
        return expectedRevision;
    }
}

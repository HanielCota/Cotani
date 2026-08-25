package com.cotani.queue.api;

/** Raised when a player or ticket conflicts with current queue state. */
public final class QueueConflictException extends QueueException {
    private static final long serialVersionUID = 1L;

    public QueueConflictException(String message) {
        super(message);
    }
}

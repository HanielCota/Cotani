package com.cotani.queue.api;

/** Raised when a queue reached its configured capacity. */
public final class QueueFullException extends QueueException {
    private static final long serialVersionUID = 1L;
    private final QueueId queueId;

    public QueueFullException(QueueId queueId) {
        super("Queue is full: "
                + java.util.Objects.requireNonNull(queueId, "queueId").value());
        this.queueId = queueId;
    }

    public QueueId queueId() {
        return queueId;
    }
}

package com.cotani.queue.api;

import java.util.Objects;

/** Base exception for expected queue-domain failures. */
public class QueueException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public QueueException(String message) {
        super(Objects.requireNonNull(message, "message"));
    }
}

package com.cotani.queue.api;

import java.io.Serializable;
import java.util.Locale;
import java.util.Objects;

/** Canonical identifier for a matchmaking queue. */
public record QueueId(String value) implements Serializable {
    private static final long serialVersionUID = 1L;

    public QueueId {
        Objects.requireNonNull(value, "value");
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("queue id must not be blank");
        }
        if (value.length() > 64) {
            throw new IllegalArgumentException("queue id must not exceed 64 characters");
        }
        if (!value.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("queue id contains unsupported characters");
        }
    }

    public static QueueId of(String value) {
        return new QueueId(value);
    }
}

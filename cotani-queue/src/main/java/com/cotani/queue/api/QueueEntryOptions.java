package com.cotani.queue.api;

import java.time.Duration;
import java.util.Objects;

/** Options captured when a player enters a queue. */
public record QueueEntryOptions(int priority, Duration lifetime) {
    public QueueEntryOptions {
        Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException("lifetime must be positive");
        }
    }

    public static QueueEntryOptions defaults() {
        return new QueueEntryOptions(0, Duration.ofMinutes(5));
    }

    public QueueEntryOptions withPriority(int nextPriority) {
        return new QueueEntryOptions(nextPriority, lifetime);
    }

    public QueueEntryOptions withLifetime(Duration nextLifetime) {
        return new QueueEntryOptions(priority, nextLifetime);
    }
}

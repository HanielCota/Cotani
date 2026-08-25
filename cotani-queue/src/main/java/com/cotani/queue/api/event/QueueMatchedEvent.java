package com.cotani.queue.api.event;

import com.cotani.queue.api.QueueMatch;
import java.util.Objects;

/** Published after tickets are atomically removed as a matchmaking group. */
public record QueueMatchedEvent(QueueMatch match) implements QueueEvent {
    public QueueMatchedEvent {
        Objects.requireNonNull(match, "match");
    }
}

package com.cotani.queue.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable ticket representing one player waiting in one queue. */
public record QueueTicket(
        UUID ticketId,
        QueueId queueId,
        UUID playerId,
        int priority,
        Instant enqueuedAt,
        Instant expiresAt,
        long sequence) {
    public QueueTicket {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(enqueuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after enqueuedAt");
        }
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(expiresAt);
    }
}

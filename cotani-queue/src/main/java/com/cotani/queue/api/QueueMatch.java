package com.cotani.queue.api;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable group atomically removed from a queue for matchmaking. */
public record QueueMatch(UUID matchId, QueueId queueId, List<QueueTicket> tickets, Instant createdAt) {
    public QueueMatch {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(tickets, "tickets");
        Objects.requireNonNull(createdAt, "createdAt");
        if (tickets.size() < 2) {
            throw new IllegalArgumentException("a queue match must contain at least two tickets");
        }
        var ticketIds = new HashSet<UUID>();
        tickets.forEach(ticket -> {
            Objects.requireNonNull(ticket, "ticket");
            if (!queueId.equals(ticket.queueId())) {
                throw new IllegalArgumentException("all match tickets must belong to the same queue");
            }
            if (!ticketIds.add(ticket.ticketId())) {
                throw new IllegalArgumentException("a queue match cannot contain the same ticket twice");
            }
        });
        if (tickets.stream().map(QueueTicket::playerId).distinct().count() != tickets.size()) {
            throw new IllegalArgumentException("a queue match cannot contain the same player twice");
        }
        tickets = List.copyOf(tickets);
    }
}

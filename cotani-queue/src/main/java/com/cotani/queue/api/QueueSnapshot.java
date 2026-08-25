package com.cotani.queue.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Immutable repository state for all queues managed by one service. */
public record QueueSnapshot(long revision, long nextSequence, List<QueueTicket> tickets) {
    public QueueSnapshot {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        if (nextSequence < 0) {
            throw new IllegalArgumentException("nextSequence must not be negative");
        }
        Objects.requireNonNull(tickets, "tickets");
        tickets.forEach(ticket -> Objects.requireNonNull(ticket, "ticket"));
        tickets = List.copyOf(tickets);
        validateTickets(tickets, nextSequence);
    }

    public QueueSnapshot(List<QueueTicket> tickets) {
        this(0, nextSequenceFor(tickets), tickets);
    }

    public static QueueSnapshot empty() {
        return new QueueSnapshot(0, 0, List.of());
    }

    private static void validateTickets(List<QueueTicket> tickets, long nextSequence) {
        var ticketIds = new HashSet<java.util.UUID>();
        var playerIds = new HashSet<java.util.UUID>();
        var sequences = new HashSet<Long>();
        tickets.forEach(ticket -> {
            if (!ticketIds.add(ticket.ticketId())) {
                throw new IllegalArgumentException("snapshot contains duplicate ticket ids");
            }
            if (!playerIds.add(ticket.playerId())) {
                throw new IllegalArgumentException("a player cannot have multiple active queue tickets");
            }
            if (!sequences.add(ticket.sequence())) {
                throw new IllegalArgumentException("snapshot contains duplicate ticket sequences");
            }
            if (ticket.sequence() >= nextSequence) {
                throw new IllegalArgumentException("nextSequence must be greater than every ticket sequence");
            }
        });
    }

    private static long nextSequenceFor(List<QueueTicket> tickets) {
        Objects.requireNonNull(tickets, "tickets");
        return tickets.stream().mapToLong(QueueTicket::sequence).max().orElse(-1) + 1;
    }
}

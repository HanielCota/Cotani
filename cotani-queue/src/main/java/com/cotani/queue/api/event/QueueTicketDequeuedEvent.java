package com.cotani.queue.api.event;

import com.cotani.queue.api.QueueTicket;
import java.util.Objects;

/** Published after a ticket is removed from a queue without matching. */
public record QueueTicketDequeuedEvent(QueueTicket ticket) implements QueueEvent {
    public QueueTicketDequeuedEvent {
        Objects.requireNonNull(ticket, "ticket");
    }
}

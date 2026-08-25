package com.cotani.queue.api.event;

import com.cotani.queue.api.QueueTicket;
import java.util.Objects;

/** Published after a ticket is committed to a queue. */
public record QueueTicketEnqueuedEvent(QueueTicket ticket) implements QueueEvent {
    public QueueTicketEnqueuedEvent {
        Objects.requireNonNull(ticket, "ticket");
    }
}

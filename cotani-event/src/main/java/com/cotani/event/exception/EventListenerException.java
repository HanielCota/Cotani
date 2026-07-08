package com.cotani.event.exception;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.subscription.EventSubscription;
import java.io.Serial;
import java.util.Objects;

public final class EventListenerException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient CotaniEvent event;
    private final transient EventSubscription subscription;

    public EventListenerException(CotaniEvent event, EventSubscription subscription, Throwable cause) {
        super("Failed to dispatch event " + event.getClass().getSimpleName(), cause);
        this.event = Objects.requireNonNull(event, "event cannot be null");
        this.subscription = Objects.requireNonNull(subscription, "subscription cannot be null");
    }

    public CotaniEvent event() {
        return event;
    }

    public EventSubscription subscription() {
        return subscription;
    }
}

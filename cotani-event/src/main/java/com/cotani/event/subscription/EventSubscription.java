package com.cotani.event.subscription;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import java.util.UUID;

public interface EventSubscription {

    UUID id();

    Class<? extends CotaniEvent> eventType();

    EventPriority priority();

    EventListener<? extends CotaniEvent> listener();

    boolean active();

    void unsubscribe();

    /**
     * Whether this subscription should ignore events that have already been cancelled.
     * Only applies to events implementing {@link com.cotani.event.cancellable.CancellableEvent}.
     *
     * @return true if the subscription ignores cancelled events, false otherwise
     */
    default boolean ignoreCancelled() {
        return false;
    }
}

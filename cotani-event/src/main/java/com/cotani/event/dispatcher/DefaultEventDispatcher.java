package com.cotani.event.dispatcher;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventListener;
import com.cotani.event.cancellable.CancellableEvent;
import com.cotani.event.exception.EventExceptionHandler;
import com.cotani.event.exception.EventListenerException;
import com.cotani.event.subscription.EventSubscription;
import java.util.List;
import java.util.Objects;

public final class DefaultEventDispatcher implements EventDispatcher {

    private final EventExceptionHandler exceptionHandler;

    public DefaultEventDispatcher(EventExceptionHandler exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler cannot be null");
    }

    @Override
    public <T extends CotaniEvent> T dispatch(T event, List<EventSubscription> subscriptions) {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(subscriptions, "subscriptions cannot be null");

        for (EventSubscription subscription : subscriptions) {
            dispatchToSubscription(event, subscription);
        }

        return event;
    }

    private <T extends CotaniEvent> void dispatchToSubscription(T event, EventSubscription subscription) {
        if (!subscription.active()) {
            return;
        }

        if (event instanceof CancellableEvent cancellable
                && cancellable.cancelled()
                && subscription.ignoreCancelled()) {
            return;
        }

        try {
            listener(subscription).handle(event);
        } catch (Exception exception) {
            exceptionHandler.handle(new EventListenerException(event, subscription, exception));
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends CotaniEvent> EventListener<T> listener(EventSubscription subscription) {
        return (EventListener<T>) subscription.listener();
    }
}

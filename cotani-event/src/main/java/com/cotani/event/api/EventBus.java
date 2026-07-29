package com.cotani.event.api;

import com.cotani.event.subscription.EventSubscription;
import java.util.concurrent.CompletionStage;

public interface EventBus extends AutoCloseable {

    /**
     * Publishes on the calling thread and returns the same event after dispatch.
     *
     * <p>Listeners run by ascending priority and, within one priority, registration order. A
     * subscription deactivated before its turn is skipped. Recursive publication is immediate.
     * Listener {@link Exception Exceptions} are sent to the configured handler; {@link Error
     * Errors} propagate to the publisher. A blocking listener therefore blocks this call.
     */
    <T extends CotaniEvent> T publish(T event);

    /**
     * Publishes on the executor supplied to the event bus.
     *
     * <p>The caller must not mutate {@code event} after this method returns unless the event type
     * itself defines a thread-safe mutation protocol. Cancellation of the returned stage does not
     * mutate listeners concurrently. Each listener is isolated and subject to the configured
     * dispatch deadline; a timed-out listener is interrupted and may be unsubscribed by policy.
     */
    <T extends CotaniEvent> CompletionStage<T> publishAsync(T event);

    <T extends CotaniEvent> EventSubscription subscribe(Class<T> eventType, EventListener<? super T> listener);

    <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, EventListener<? super T> listener);

    <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, boolean ignoreCancelled, EventListener<? super T> listener);

    default <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, boolean ignoreCancelled, EventListener<? super T> listener) {
        return subscribe(eventType, EventPriority.NORMAL, ignoreCancelled, listener);
    }

    void unsubscribe(EventSubscription subscription);

    void clear();

    @Override
    default void close() {
        clear();
    }
}

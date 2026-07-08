package com.cotani.event.subscription;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A container for multiple event subscriptions that allows them to be unsubscribed in bulk.
 *
 * <p>This implementation is thread-safe and implements {@link AutoCloseable}.</p>
 */
public final class CompositeEventSubscription implements AutoCloseable {

    private final ConcurrentMap<EventSubscription, Boolean> subscriptions = new ConcurrentHashMap<>();
    private volatile boolean closed = false;

    /**
     * Creates a new empty composite subscription.
     */
    public CompositeEventSubscription() {}

    /**
     * Adds an event subscription to this composite subscription.
     *
     * <p>If this composite subscription has already been unsubscribed/closed, the added
     * subscription will be unsubscribed immediately.</p>
     *
     * @param subscription the subscription to add
     */
    public void add(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");
        if (closed) {
            subscription.unsubscribe();
            return;
        }
        subscriptions.put(subscription, Boolean.TRUE);
    }

    /**
     * Removes an event subscription from this composite subscription without unsubscribing it.
     *
     * @param subscription the subscription to remove
     */
    public void remove(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");
        subscriptions.remove(subscription);
    }

    /**
     * Unsubscribes all subscriptions currently contained in this composite subscription.
     */
    public void unsubscribeAll() {
        closed = true;
        subscriptions.keySet().forEach(EventSubscription::unsubscribe);
        subscriptions.clear();
    }

    @Override
    public void close() {
        unsubscribeAll();
    }

    /**
     * Returns the number of subscriptions currently held by this composite subscription.
     *
     * @return the number of subscriptions
     */
    public int size() {
        return subscriptions.size();
    }
}

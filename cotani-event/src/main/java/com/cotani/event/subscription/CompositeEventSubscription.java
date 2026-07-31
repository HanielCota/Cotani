package com.cotani.event.subscription;

import com.cotani.api.InternalApi;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@InternalApi
public final class CompositeEventSubscription implements AutoCloseable {
    private final Set<EventSubscription> subscriptions = ConcurrentHashMap.newKeySet();
    private volatile boolean closed = false;

    public CompositeEventSubscription() {}

    public void add(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");

        if (closed) {
            subscription.unsubscribe();
            return;
        }
        subscriptions.add(subscription);
    }

    public void remove(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");

        subscriptions.remove(subscription);
    }

    public void unsubscribeAll() {
        closed = true;
        subscriptions.forEach(EventSubscription::unsubscribe);
        subscriptions.clear();
    }

    @Override
    public void close() {
        unsubscribeAll();
    }

    public int size() {
        return subscriptions.size();
    }
}

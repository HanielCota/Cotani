package com.cotani.event.registry;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.subscription.EventSubscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultEventRegistry implements EventRegistry {

    private final CopyOnWriteArrayList<EventSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<Class<? extends CotaniEvent>, List<EventSubscription>> resolvedCache =
            new ConcurrentHashMap<>();

    @Override
    public void register(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");

        subscriptions.add(subscription);
        resolvedCache.clear();
    }

    @Override
    public void unregister(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");

        subscription.unsubscribe();
        subscriptions.removeIf(current -> current.id().equals(subscription.id()));
        resolvedCache.clear();
    }

    @Override
    public List<EventSubscription> subscriptionsFor(CotaniEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        Class<? extends CotaniEvent> eventClass = event.getClass();
        List<EventSubscription> cached = resolvedCache.get(eventClass);
        if (cached != null) {
            boolean hasInactive = false;
            for (EventSubscription sub : cached) {
                if (!sub.active()) {
                    hasInactive = true;
                    break;
                }
            }
            if (!hasInactive) {
                return cached;
            }
            resolvedCache.remove(eventClass);
            removeInactive();
        }

        return resolvedCache.computeIfAbsent(eventClass, this::resolveSubscriptions);
    }

    private List<EventSubscription> resolveSubscriptions(Class<? extends CotaniEvent> eventClass) {
        List<EventSubscription> matchingSubscriptions = new ArrayList<>();

        for (EventSubscription subscription : subscriptions) {
            if (subscription.active() && subscription.eventType().isAssignableFrom(eventClass)) {
                matchingSubscriptions.add(subscription);
            }
        }

        matchingSubscriptions.sort(Comparator.comparing(EventSubscription::priority));
        return List.copyOf(matchingSubscriptions);
    }

    @Override
    public void removeInactive() {
        if (subscriptions.removeIf(subscription -> !subscription.active())) {
            resolvedCache.clear();
        }
    }

    @Override
    public void clear() {
        subscriptions.forEach(EventSubscription::unsubscribe);
        subscriptions.clear();
        resolvedCache.clear();
    }
}

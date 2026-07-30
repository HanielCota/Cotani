package com.cotani.event.registry;

import com.cotani.api.InternalApi;
import com.cotani.event.api.CotaniEvent;
import com.cotani.event.subscription.EventSubscription;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@InternalApi
public final class DefaultEventRegistry implements EventRegistry {

    private final CopyOnWriteArrayList<EventSubscription> subscriptions = new CopyOnWriteArrayList<>();
    private final Map<UUID, EventSubscription> subscriptionIndex = new ConcurrentHashMap<>();
    private final Map<Class<? extends CotaniEvent>, List<EventSubscription>> resolvedCache = new ConcurrentHashMap<>();
    private final Map<Class<? extends CotaniEvent>, AtomicInteger> inactiveCounters = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock snapshotLock = new ReentrantReadWriteLock();

    @Override
    public void register(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");
        snapshotLock.writeLock().lock();
        try {
            subscriptions.add(subscription);
            subscriptionIndex.put(subscription.id(), subscription);
            resolvedCache.clear();
            inactiveCounters.clear();
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    @Override
    public void unregister(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");

        snapshotLock.writeLock().lock();
        try {
            subscription.unsubscribe();
            subscriptionIndex.remove(subscription.id());
            if (subscriptions.remove(subscription)) {
                inactiveCounters.clear();
                resolvedCache.clear();
            }
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    @Override
    public List<EventSubscription> subscriptionsFor(CotaniEvent event) {
        Objects.requireNonNull(event, "event cannot be null");

        snapshotLock.readLock().lock();
        try {
            Class<? extends CotaniEvent> eventClass = event.getClass();
            List<EventSubscription> cached = resolvedCache.get(eventClass);
            var inactiveCounter = inactiveCounters.get(eventClass);
            if (cached != null && (inactiveCounter == null || inactiveCounter.get() == 0)) {
                return cached;
            }

            return resolvedCache.computeIfAbsent(eventClass, cls -> {
                var result = resolveSubscriptions(cls);
                inactiveCounters.put(cls, new AtomicInteger(countInactive(result)));
                return result;
            });
        } finally {
            snapshotLock.readLock().unlock();
        }
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

    private static int countInactive(List<EventSubscription> subs) {
        int count = 0;
        for (var s : subs) {
            if (!s.active()) count++;
        }
        return count;
    }

    @Override
    public void removeInactive() {
        snapshotLock.writeLock().lock();
        try {
            boolean changed = subscriptions.removeIf(subscription -> {
                if (!subscription.active()) {
                    subscriptionIndex.remove(subscription.id());
                    return true;
                }
                return false;
            });
            if (changed) {
                resolvedCache.clear();
                inactiveCounters.clear();
            }
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }

    @Override
    public void clear() {
        snapshotLock.writeLock().lock();
        try {
            subscriptions.forEach(EventSubscription::unsubscribe);
            subscriptions.clear();
            subscriptionIndex.clear();
            resolvedCache.clear();
            inactiveCounters.clear();
        } finally {
            snapshotLock.writeLock().unlock();
        }
    }
}

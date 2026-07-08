package com.cotani.event.subscription;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DefaultEventSubscription implements EventSubscription {

    private final UUID id;
    private final Class<? extends CotaniEvent> eventType;
    private final EventPriority priority;
    private final boolean ignoreCancelled;
    private final EventListener<? extends CotaniEvent> listener;
    private final AtomicBoolean active;

    public DefaultEventSubscription(
            UUID id,
            Class<? extends CotaniEvent> eventType,
            EventPriority priority,
            EventListener<? extends CotaniEvent> listener) {
        this(id, eventType, priority, false, listener);
    }

    public DefaultEventSubscription(
            UUID id,
            Class<? extends CotaniEvent> eventType,
            EventPriority priority,
            boolean ignoreCancelled,
            EventListener<? extends CotaniEvent> listener) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType cannot be null");
        this.priority = Objects.requireNonNull(priority, "priority cannot be null");
        this.ignoreCancelled = ignoreCancelled;
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
        this.active = new AtomicBoolean(true);
    }

    public static DefaultEventSubscription create(
            Class<? extends CotaniEvent> eventType,
            EventPriority priority,
            EventListener<? extends CotaniEvent> listener) {
        return create(eventType, priority, false, listener);
    }

    public static DefaultEventSubscription create(
            Class<? extends CotaniEvent> eventType,
            EventPriority priority,
            boolean ignoreCancelled,
            EventListener<? extends CotaniEvent> listener) {
        return new DefaultEventSubscription(UUID.randomUUID(), eventType, priority, ignoreCancelled, listener);
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public Class<? extends CotaniEvent> eventType() {
        return eventType;
    }

    @Override
    public EventPriority priority() {
        return priority;
    }

    @Override
    public boolean ignoreCancelled() {
        return ignoreCancelled;
    }

    @Override
    public EventListener<? extends CotaniEvent> listener() {
        return listener;
    }

    @Override
    public boolean active() {
        return active.get();
    }

    @Override
    public void unsubscribe() {
        active.set(false);
    }
}

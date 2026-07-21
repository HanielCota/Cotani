package com.cotani.event.bus;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.dispatcher.DefaultEventDispatcher;
import com.cotani.event.dispatcher.EventDispatcher;
import com.cotani.event.exception.EventExceptionHandler;
import com.cotani.event.registry.DefaultEventRegistry;
import com.cotani.event.registry.EventRegistry;
import com.cotani.event.subscription.DefaultEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

public final class DefaultEventBus implements EventBus {

    private final EventRegistry registry;
    private final EventDispatcher dispatcher;
    private final Executor asyncExecutor;

    public DefaultEventBus(EventRegistry registry, EventDispatcher dispatcher, Executor asyncExecutor) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
    }

    public static DefaultEventBus create(EventExceptionHandler exceptionHandler, Executor asyncExecutor) {
        Objects.requireNonNull(exceptionHandler, "exceptionHandler cannot be null");
        Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");

        return new DefaultEventBus(
                new DefaultEventRegistry(), new DefaultEventDispatcher(exceptionHandler), asyncExecutor);
    }

    @Override
    public <T extends CotaniEvent> T publish(T event) {
        Objects.requireNonNull(event, "event cannot be null");

        return dispatcher.dispatch(event, registry.subscriptionsFor(event));
    }

    @Override
    public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
        Objects.requireNonNull(event, "event cannot be null");

        return CompletableFuture.supplyAsync(() -> publish(event), asyncExecutor);
    }

    @Override
    public <T extends CotaniEvent> EventSubscription subscribe(Class<T> eventType, EventListener<? super T> listener) {
        return subscribe(eventType, EventPriority.NORMAL, false, listener);
    }

    @Override
    public <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, EventListener<? super T> listener) {
        return subscribe(eventType, priority, false, listener);
    }

    @Override
    public <T extends CotaniEvent> EventSubscription subscribe(
            Class<T> eventType, EventPriority priority, boolean ignoreCancelled, EventListener<? super T> listener) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        EventSubscription subscription =
                DefaultEventSubscription.create(eventType, priority, ignoreCancelled, listener);

        registry.register(subscription);
        return subscription;
    }

    @Override
    public void unsubscribe(EventSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription cannot be null");

        registry.unregister(subscription);
    }

    @Override
    public void clear() {
        registry.clear();
    }
}

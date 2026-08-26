package com.cotani.event.bus;

import com.cotani.api.InternalApi;
import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventDispatchPolicy;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@InternalApi
public final class DefaultEventBus implements EventBus {
    private final EventRegistry registry;
    private final EventDispatcher dispatcher;
    private final Executor asyncExecutor;
    private final Optional<ExecutorService> ownedListenerExecutor;
    private final Optional<ExecutorService> ownedAsyncExecutor;

    private DefaultEventBus(EventRegistry registry, EventDispatcher dispatcher, Executor asyncExecutor) {
        this(registry, dispatcher, asyncExecutor, Optional.empty(), Optional.empty());
    }

    public static DefaultEventBus create(EventRegistry registry, EventDispatcher dispatcher, Executor asyncExecutor) {
        return new DefaultEventBus(registry, dispatcher, asyncExecutor);
    }

    private DefaultEventBus(
            EventRegistry registry,
            EventDispatcher dispatcher,
            Executor asyncExecutor,
            Optional<ExecutorService> ownedListenerExecutor,
            Optional<ExecutorService> ownedAsyncExecutor) {
        this.registry = Objects.requireNonNull(registry, "registry cannot be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher cannot be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        this.ownedListenerExecutor = Objects.requireNonNull(ownedListenerExecutor, "ownedListenerExecutor");
        this.ownedAsyncExecutor = Objects.requireNonNull(ownedAsyncExecutor, "ownedAsyncExecutor");
    }

    public static DefaultEventBus create(EventExceptionHandler exceptionHandler, Executor asyncExecutor) {
        return create(exceptionHandler, asyncExecutor, EventDispatchPolicy.defaults());
    }

    /**
     * Creates a bus that owns the given async executor.
     *
     * <p>The executor is shut down by {@link #close()} together with the bus-owned listener
     * executor, so resources created by this factory are never leaked to the caller.
     *
     * @param exceptionHandler handler for listener failures
     * @param ownedAsyncExecutor executor used for {@code publishAsync}; closed with the bus
     * @return a new event bus owning both internal executors
     */
    public static DefaultEventBus createOwning(
            EventExceptionHandler exceptionHandler, ExecutorService ownedAsyncExecutor) {
        return createOwning(exceptionHandler, ownedAsyncExecutor, EventDispatchPolicy.defaults());
    }

    /**
     * Creates a bus that owns the given async executor with a custom dispatch policy.
     *
     * @param exceptionHandler handler for listener failures
     * @param ownedAsyncExecutor executor used for {@code publishAsync}; closed with the bus
     * @param policy dispatch policy
     * @return a new event bus owning both internal executors
     */
    public static DefaultEventBus createOwning(
            EventExceptionHandler exceptionHandler, ExecutorService ownedAsyncExecutor, EventDispatchPolicy policy) {
        Objects.requireNonNull(exceptionHandler, "exceptionHandler cannot be null");
        Objects.requireNonNull(ownedAsyncExecutor, "ownedAsyncExecutor cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");

        var listenerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return new DefaultEventBus(
                new DefaultEventRegistry(),
                new DefaultEventDispatcher(exceptionHandler, listenerExecutor, policy),
                ownedAsyncExecutor,
                Optional.of(listenerExecutor),
                Optional.of(ownedAsyncExecutor));
    }

    public static DefaultEventBus create(
            EventExceptionHandler exceptionHandler, Executor asyncExecutor, EventDispatchPolicy policy) {
        Objects.requireNonNull(exceptionHandler, "exceptionHandler cannot be null");
        Objects.requireNonNull(asyncExecutor, "asyncExecutor cannot be null");
        Objects.requireNonNull(policy, "policy cannot be null");

        var listenerExecutor = Executors.newVirtualThreadPerTaskExecutor();
        return new DefaultEventBus(
                new DefaultEventRegistry(),
                new DefaultEventDispatcher(exceptionHandler, listenerExecutor, policy),
                asyncExecutor,
                Optional.of(listenerExecutor),
                Optional.empty());
    }

    @Override
    public <T extends CotaniEvent> T publish(T event) {
        Objects.requireNonNull(event, "event cannot be null");

        return dispatcher.dispatch(event, registry.subscriptionsFor(event));
    }

    @Override
    public <T extends CotaniEvent> CompletionStage<T> publishAsync(T event) {
        Objects.requireNonNull(event, "event cannot be null");

        var kickoff = new CompletableFuture<Void>();

        try {
            asyncExecutor.execute(() -> kickoff.complete(null));
        } catch (RuntimeException schedulingFailure) {
            kickoff.completeExceptionally(schedulingFailure);
        }
        return kickoff.thenCompose(_ -> dispatcher.dispatchAsync(event, registry.subscriptionsFor(event)));
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

        java.util.concurrent.atomic.AtomicReference<EventSubscription> ref =
                new java.util.concurrent.atomic.AtomicReference<>();
        EventSubscription subscription =
                DefaultEventSubscription.create(eventType, priority, ignoreCancelled, listener, () -> {
                    var sub = ref.get();
                    if (sub != null) {
                        registry.unregister(sub);
                    }
                });
        ref.set(subscription);
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

    @Override
    public void close() {
        clear();
        ownedListenerExecutor.ifPresent(ExecutorService::shutdownNow);
        ownedAsyncExecutor.ifPresent(ExecutorService::shutdownNow);
    }
}

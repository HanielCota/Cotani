package com.cotani.event.bus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventBus;
import com.cotani.event.api.EventDispatchPolicy;
import com.cotani.event.api.EventListener;
import com.cotani.event.api.EventPriority;
import com.cotani.event.dispatcher.DefaultEventDispatcher;
import com.cotani.event.exception.EventExceptionHandler;
import com.cotani.event.exception.EventListenerException;
import com.cotani.event.registry.DefaultEventRegistry;
import com.cotani.event.registry.EventRegistry;
import com.cotani.event.subscription.EventSubscription;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class DefaultEventBusTest {

    @Test
    void shouldExecuteListenersInPriorityOrder() {
        EventBus bus = directBus(exception -> {});
        List<String> order = new ArrayList<>();
        bus.subscribe(TestEvent.class, EventPriority.HIGHEST, event -> order.add("highest"));
        bus.subscribe(TestEvent.class, EventPriority.LOWEST, event -> order.add("lowest"));
        bus.subscribe(TestEvent.class, EventPriority.NORMAL, event -> order.add("normal"));
        bus.subscribe(TestEvent.class, event -> order.add("default-normal"));

        bus.publish(new TestEvent());

        assertEquals(List.of("lowest", "normal", "default-normal", "highest"), order);
    }

    @Test
    void shouldExecuteListenersInPriorityOrderWhenPublishingAsync() throws Exception {
        EventBus bus = directBus(exception -> {});
        List<String> order = new ArrayList<>();
        bus.subscribe(TestEvent.class, EventPriority.HIGH, event -> order.add("high"));
        bus.subscribe(TestEvent.class, EventPriority.LOW, event -> order.add("low"));
        bus.subscribe(TestEvent.class, EventPriority.HIGHEST, event -> order.add("highest"));

        bus.publishAsync(new TestEvent()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertEquals(List.of("low", "high", "highest"), order);
    }

    @Test
    void shouldPublishAsyncOnProvidedExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var bus = DefaultEventBus.create(
                    new DefaultEventRegistry(),
                    new DefaultEventDispatcher(exception -> {}, executor, EventDispatchPolicy.defaults()),
                    executor);
            AtomicBoolean called = new AtomicBoolean(false);
            bus.subscribe(TestEvent.class, event -> called.set(true));
            TestEvent event = new TestEvent();

            TestEvent result = bus.publishAsync(event).toCompletableFuture().get(5, TimeUnit.SECONDS);

            assertSame(event, result);
            assertTrue(called.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldUseNormalAsDefaultPriorityWhenSubscribingWithoutPriority() {
        EventBus bus = directBus(exception -> {});

        EventSubscription subscription = bus.subscribe(TestEvent.class, event -> {});

        assertSame(EventPriority.NORMAL, subscription.priority());
    }

    @Test
    void shouldNotInvokeListenerAfterUnsubscribe() {
        EventBus bus = directBus(exception -> {});
        AtomicBoolean called = new AtomicBoolean(false);
        EventSubscription subscription = bus.subscribe(TestEvent.class, event -> called.set(true));

        bus.unsubscribe(subscription);
        bus.publish(new TestEvent());

        assertFalse(called.get());
    }

    @Test
    void shouldPropagateListenerFailureToHandlerAndContinueDispatch() {
        EventExceptionHandler handler = mock(EventExceptionHandler.class);
        EventBus bus = directBus(handler);
        List<String> order = new ArrayList<>();
        bus.subscribe(TestEvent.class, EventPriority.LOWEST, event -> {
            throw new IllegalStateException("boom");
        });
        bus.subscribe(TestEvent.class, EventPriority.HIGHEST, event -> order.add("continued"));
        TestEvent event = new TestEvent();

        TestEvent result = bus.publish(event);

        assertSame(event, result);
        assertEquals(List.of("continued"), order);
        ArgumentCaptor<EventListenerException> captor = ArgumentCaptor.forClass(EventListenerException.class);
        verify(handler).handle(captor.capture());
        assertSame(event, captor.getValue().event());
        assertInstanceOf(IllegalStateException.class, captor.getValue().getCause());
    }

    @Test
    void shouldPropagateErrorToPublisher() {
        EventBus bus = directBus(exception -> {});
        bus.subscribe(TestEvent.class, event -> {
            throw new AssertionError("boom");
        });

        assertThrows(AssertionError.class, () -> bus.publish(new TestEvent()));
    }

    @Test
    void shouldSkipSubscriptionUnsubscribedDuringPublish() {
        EventBus bus = directBus(exception -> {});
        List<String> order = new ArrayList<>();
        EventSubscription second = bus.subscribe(TestEvent.class, EventPriority.HIGHEST, event -> order.add("second"));
        bus.subscribe(TestEvent.class, EventPriority.LOWEST, event -> {
            order.add("first");
            second.unsubscribe();
        });

        bus.publish(new TestEvent());

        assertEquals(List.of("first"), order);
    }

    @Test
    void shouldDeliverSubtypeEventToSupertypeListener() {
        EventBus bus = directBus(exception -> {});
        AtomicBoolean called = new AtomicBoolean(false);
        bus.subscribe(BaseEvent.class, event -> called.set(true));

        bus.publish(new ChildEvent());

        assertTrue(called.get());
    }

    @Test
    void shouldNotDeliverSupertypeEventToSubtypeListener() {
        EventBus bus = directBus(exception -> {});
        AtomicBoolean called = new AtomicBoolean(false);
        bus.subscribe(ChildEvent.class, event -> called.set(true));

        bus.publish(new BaseEvent());

        assertFalse(called.get());
    }

    @Test
    void shouldReturnSameEventWhenNoListeners() {
        EventBus bus = directBus(exception -> {});
        TestEvent event = new TestEvent();

        TestEvent result = bus.publish(event);

        assertSame(event, result);
    }

    @Test
    void shouldCompleteAsyncPublishWhenNoListeners() throws Exception {
        EventBus bus = directBus(exception -> {});
        TestEvent event = new TestEvent();

        TestEvent result = bus.publishAsync(event).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertSame(event, result);
    }

    @Test
    void shouldCompleteExceptionallyWhenAsyncExecutorRejectsScheduling() throws Exception {
        Executor rejecting = task -> {
            throw new RejectedExecutionException("executor is closed");
        };
        try (var bus = DefaultEventBus.create(exception -> {}, rejecting)) {
            var stage = bus.publishAsync(new TestEvent()).toCompletableFuture();

            ExecutionException failure = assertThrows(ExecutionException.class, () -> stage.get(5, TimeUnit.SECONDS));

            assertInstanceOf(RejectedExecutionException.class, failure.getCause());
        }
    }

    @Test
    @SuppressWarnings("try")
    void shouldClearSubscriptionsOnClose() {
        EventBus bus = directBus(exception -> {});
        AtomicBoolean called = new AtomicBoolean(false);
        bus.subscribe(TestEvent.class, event -> called.set(true));

        bus.close();
        bus.close();
        bus.publish(new TestEvent());

        assertFalse(called.get());
    }

    @Test
    @SuppressWarnings("try")
    void shouldShutdownOwnedListenerExecutorOnClose() throws Exception {
        try (var bus = DefaultEventBus.create(exception -> {}, Runnable::run)) {
            bus.close();
            bus.subscribe(TestEvent.class, event -> {});

            var stage = bus.publishAsync(new TestEvent()).toCompletableFuture();

            ExecutionException failure = assertThrows(ExecutionException.class, () -> stage.get(5, TimeUnit.SECONDS));

            assertInstanceOf(RejectedExecutionException.class, failure.getCause());
        }
    }

    @Test
    void shouldPreserveExternalExecutorAfterClose() {
        EventBus bus = directBus(exception -> {});
        bus.close();
        AtomicBoolean called = new AtomicBoolean(false);
        bus.subscribe(TestEvent.class, event -> called.set(true));

        bus.publish(new TestEvent());

        assertTrue(called.get());
    }

    @Test
    void shouldRegisterSubscriptionsWithPriorityInRegistry() {
        EventRegistry registry = mock(EventRegistry.class);
        EventBus bus = busWith(registry);
        EventListener<TestEvent> listener = event -> {};

        EventSubscription subscription = bus.subscribe(TestEvent.class, EventPriority.HIGHEST, listener);

        ArgumentCaptor<EventSubscription> captor = ArgumentCaptor.forClass(EventSubscription.class);
        verify(registry).register(captor.capture());
        assertSame(subscription, captor.getValue());
        assertSame(EventPriority.HIGHEST, subscription.priority());
        assertSame(listener, subscription.listener());
        assertEquals(TestEvent.class, subscription.eventType());
    }

    @Test
    void shouldUnregisterSubscriptionOnUnsubscribe() {
        EventRegistry registry = mock(EventRegistry.class);
        EventBus bus = busWith(registry);
        EventSubscription subscription = bus.subscribe(TestEvent.class, event -> {});

        bus.unsubscribe(subscription);

        verify(registry).unregister(subscription);
    }

    @Test
    void shouldClearRegistryOnClose() {
        EventRegistry registry = mock(EventRegistry.class);
        EventBus bus = busWith(registry);

        bus.close();

        verify(registry).clear();
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        EventBus bus = directBus(exception -> {});
        assertThrows(NullPointerException.class, () -> bus.publish(null));
        assertThrows(NullPointerException.class, () -> bus.<TestEvent>subscribe(null, event -> {}));
        assertThrows(NullPointerException.class, () -> bus.subscribe(TestEvent.class, null, event -> {}));
        assertThrows(NullPointerException.class, () -> bus.subscribe(TestEvent.class, EventPriority.NORMAL, null));
        assertThrows(NullPointerException.class, () -> bus.unsubscribe(null));
        assertThrows(NullPointerException.class, () -> DefaultEventBus.create(null, Runnable::run));
        assertThrows(NullPointerException.class, () -> DefaultEventBus.create(exception -> {}, null));
        assertThrows(NullPointerException.class, () -> DefaultEventBus.create(exception -> {}, Runnable::run, null));
        assertThrows(
                NullPointerException.class,
                () -> DefaultEventBus.create(
                        null,
                        new DefaultEventDispatcher(exception -> {}, Runnable::run, EventDispatchPolicy.defaults()),
                        Runnable::run));
        assertThrows(
                NullPointerException.class,
                () -> DefaultEventBus.create(new DefaultEventRegistry(), null, Runnable::run));
        assertThrows(
                NullPointerException.class,
                () -> DefaultEventBus.create(
                        new DefaultEventRegistry(),
                        new DefaultEventDispatcher(exception -> {}, Runnable::run, EventDispatchPolicy.defaults()),
                        null));
    }

    private static DefaultEventBus directBus(EventExceptionHandler handler) {
        return DefaultEventBus.create(
                new DefaultEventRegistry(),
                new DefaultEventDispatcher(handler, Runnable::run, EventDispatchPolicy.defaults()),
                Runnable::run);
    }

    private static DefaultEventBus busWith(EventRegistry registry) {
        return DefaultEventBus.create(
                registry,
                new DefaultEventDispatcher(exception -> {}, Runnable::run, EventDispatchPolicy.defaults()),
                Runnable::run);
    }

    private record TestEvent() implements CotaniEvent {}

    private static class BaseEvent implements CotaniEvent {}

    private static final class ChildEvent extends BaseEvent {}
}

package com.cotani.event.dispatcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventDispatchPolicy;
import com.cotani.event.api.EventPriority;
import com.cotani.event.cancellable.AbstractCancellableEvent;
import com.cotani.event.exception.EventExceptionHandler;
import com.cotani.event.exception.EventListenerException;
import com.cotani.event.subscription.DefaultEventSubscription;
import com.cotani.event.subscription.EventSubscription;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class DefaultEventDispatcherTest {

    @Test
    void shouldDispatchSynchronouslyInRegistrationOrder() {
        List<String> order = new ArrayList<>();
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(exception -> {});
        List<EventSubscription> subscriptions = List.of(
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> order.add("first")),
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> order.add("second")));
        TestEvent event = new TestEvent();

        TestEvent result = dispatcher.dispatch(event, subscriptions);

        assertSame(event, result);
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void shouldDispatchAsyncInRegistrationOrderAndReturnEvent() throws Exception {
        List<String> order = new ArrayList<>();
        DefaultEventDispatcher dispatcher =
                new DefaultEventDispatcher(exception -> {}, Runnable::run, EventDispatchPolicy.defaults());
        List<EventSubscription> subscriptions = List.of(
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> order.add("first")),
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> order.add("second")));
        TestEvent event = new TestEvent();

        TestEvent result = dispatcher
                .dispatchAsync(event, subscriptions)
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertSame(event, result);
        assertEquals(List.of("first", "second"), order);
    }

    @Test
    void shouldCaptureListenerExceptionAndContinueDispatch() {
        EventExceptionHandler handler = mock(EventExceptionHandler.class);
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(handler);
        List<String> order = new ArrayList<>();
        EventSubscription failing = DefaultEventSubscription.create(TestEvent.class, EventPriority.LOWEST, event -> {
            throw new IllegalStateException("boom");
        });
        EventSubscription continuing = DefaultEventSubscription.create(
                TestEvent.class, EventPriority.HIGHEST, event -> order.add("continued"));
        TestEvent event = new TestEvent();

        dispatcher.dispatch(event, List.of(failing, continuing));

        assertEquals(List.of("continued"), order);
        ArgumentCaptor<EventListenerException> captor = ArgumentCaptor.forClass(EventListenerException.class);
        verify(handler).handle(captor.capture());
        assertSame(event, captor.getValue().event());
        assertSame(failing, captor.getValue().subscription());
        assertInstanceOf(IllegalStateException.class, captor.getValue().getCause());
    }

    @Test
    void shouldCaptureListenerExceptionWhenDispatchingAsync() throws Exception {
        EventExceptionHandler handler = mock(EventExceptionHandler.class);
        DefaultEventDispatcher dispatcher =
                new DefaultEventDispatcher(handler, Runnable::run, EventDispatchPolicy.defaults());
        List<String> order = new ArrayList<>();
        EventSubscription failing = DefaultEventSubscription.create(TestEvent.class, EventPriority.LOWEST, event -> {
            throw new IllegalStateException("boom");
        });
        EventSubscription continuing = DefaultEventSubscription.create(
                TestEvent.class, EventPriority.HIGHEST, event -> order.add("continued"));
        TestEvent event = new TestEvent();

        dispatcher
                .dispatchAsync(event, List.of(failing, continuing))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals(List.of("continued"), order);
        ArgumentCaptor<EventListenerException> captor = ArgumentCaptor.forClass(EventListenerException.class);
        verify(handler).handle(captor.capture());
        assertSame(event, captor.getValue().event());
        assertSame(failing, captor.getValue().subscription());
        assertInstanceOf(IllegalStateException.class, captor.getValue().getCause());
    }

    @Test
    void shouldPropagateListenerErrorToPublisher() {
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(exception -> {});
        EventSubscription failing = DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> {
            throw new AssertionError("boom");
        });

        assertThrows(AssertionError.class, () -> dispatcher.dispatch(new TestEvent(), List.of(failing)));
    }

    @Test
    void shouldSkipInactiveSubscriptions() {
        List<String> order = new ArrayList<>();
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(exception -> {});
        EventSubscription inactive =
                DefaultEventSubscription.create(TestEvent.class, EventPriority.LOWEST, event -> order.add("inactive"));
        inactive.unsubscribe();
        EventSubscription active =
                DefaultEventSubscription.create(TestEvent.class, EventPriority.HIGHEST, event -> order.add("active"));

        dispatcher.dispatch(new TestEvent(), List.of(inactive, active));

        assertEquals(List.of("active"), order);
    }

    @Test
    void shouldSkipCancelledEventWhenSubscriptionIgnoresCancelled() {
        List<String> order = new ArrayList<>();
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(exception -> {});
        EventSubscription ignoring = DefaultEventSubscription.create(
                TestEvent.class, EventPriority.NORMAL, true, event -> order.add("ignoring"));
        EventSubscription observing = DefaultEventSubscription.create(
                TestEvent.class, EventPriority.NORMAL, false, event -> order.add("observing"));
        TestCancellableEvent event = new TestCancellableEvent();
        event.cancel();

        dispatcher.dispatch(event, List.of(observing, ignoring));

        assertEquals(List.of("observing"), order);
    }

    @Test
    void shouldSkipCancelledEventWhenDispatchingAsync() throws Exception {
        List<String> order = new ArrayList<>();
        DefaultEventDispatcher dispatcher =
                new DefaultEventDispatcher(exception -> {}, Runnable::run, EventDispatchPolicy.defaults());
        EventSubscription ignoring = DefaultEventSubscription.create(
                TestEvent.class, EventPriority.NORMAL, true, event -> order.add("ignoring"));
        EventSubscription observing = DefaultEventSubscription.create(
                TestEvent.class, EventPriority.NORMAL, false, event -> order.add("observing"));
        TestCancellableEvent event = new TestCancellableEvent();
        event.cancel();

        dispatcher
                .dispatchAsync(event, List.of(observing, ignoring))
                .toCompletableFuture()
                .get(5, TimeUnit.SECONDS);

        assertEquals(List.of("observing"), order);
    }

    @Test
    void shouldCompleteExceptionallyWhenIsolatedExecutorRejects() throws Exception {
        EventExceptionHandler handler = mock(EventExceptionHandler.class);
        Executor rejecting = task -> {
            throw new RejectedExecutionException("no capacity");
        };
        DefaultEventDispatcher dispatcher =
                new DefaultEventDispatcher(handler, rejecting, EventDispatchPolicy.defaults());
        EventSubscription subscription =
                DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> {});

        var stage =
                dispatcher.dispatchAsync(new TestEvent(), List.of(subscription)).toCompletableFuture();

        ExecutionException failure = assertThrows(ExecutionException.class, () -> stage.get(5, TimeUnit.SECONDS));
        assertInstanceOf(RejectedExecutionException.class, failure.getCause());
        verify(handler, never()).handle(any());
    }

    @Test
    void shouldTimeoutSlowListenerAndContinueWhenPolicyUnsubscribes() throws Exception {
        EventExceptionHandler handler = mock(EventExceptionHandler.class);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            DefaultEventDispatcher dispatcher =
                    new DefaultEventDispatcher(handler, executor, new EventDispatchPolicy(Duration.ofMillis(20), true));
            CountDownLatch blocker = new CountDownLatch(1);
            EventSubscription slow = DefaultEventSubscription.create(TestEvent.class, EventPriority.LOWEST, event -> {
                try {
                    blocker.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            AtomicInteger laterCalls = new AtomicInteger();
            EventSubscription fast = DefaultEventSubscription.create(
                    TestEvent.class, EventPriority.HIGHEST, event -> laterCalls.incrementAndGet());

            dispatcher
                    .dispatchAsync(new TestEvent(), List.of(slow, fast))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertFalse(slow.active());
            assertEquals(1, laterCalls.get());
            ArgumentCaptor<EventListenerException> captor = ArgumentCaptor.forClass(EventListenerException.class);
            verify(handler).handle(captor.capture());
            assertInstanceOf(TimeoutException.class, captor.getValue().getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldKeepSubscriptionActiveWhenPolicyDoesNotUnsubscribeOnTimeout() throws Exception {
        EventExceptionHandler handler = mock(EventExceptionHandler.class);
        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(
                    handler, executor, new EventDispatchPolicy(Duration.ofMillis(20), false));
            EventSubscription slow = DefaultEventSubscription.create(TestEvent.class, EventPriority.NORMAL, event -> {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });

            dispatcher
                    .dispatchAsync(new TestEvent(), List.of(slow))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);

            assertTrue(slow.active());
            ArgumentCaptor<EventListenerException> captor = ArgumentCaptor.forClass(EventListenerException.class);
            verify(handler).handle(captor.capture());
            assertInstanceOf(TimeoutException.class, captor.getValue().getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldCompleteAsyncDispatchWithEmptySubscriptions() throws Exception {
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(exception -> {});
        TestEvent event = new TestEvent();

        TestEvent result =
                dispatcher.dispatchAsync(event, List.of()).toCompletableFuture().get(5, TimeUnit.SECONDS);

        assertSame(event, result);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        DefaultEventDispatcher dispatcher = new DefaultEventDispatcher(exception -> {});
        assertThrows(NullPointerException.class, () -> dispatcher.<TestEvent>dispatch(null, List.of()));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatch(new TestEvent(), null));
        assertThrows(NullPointerException.class, () -> dispatcher.<TestEvent>dispatchAsync(null, List.of()));
        assertThrows(NullPointerException.class, () -> dispatcher.dispatchAsync(new TestEvent(), null));
        assertThrows(NullPointerException.class, () -> new DefaultEventDispatcher(null));
        assertThrows(
                NullPointerException.class,
                () -> new DefaultEventDispatcher(exception -> {}, null, EventDispatchPolicy.defaults()));
        assertThrows(
                NullPointerException.class, () -> new DefaultEventDispatcher(exception -> {}, Runnable::run, null));
    }

    private record TestEvent() implements CotaniEvent {}

    private static final class TestCancellableEvent extends AbstractCancellableEvent {}
}

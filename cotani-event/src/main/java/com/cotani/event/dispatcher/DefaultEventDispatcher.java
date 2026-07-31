package com.cotani.event.dispatcher;

import com.cotani.api.InternalApi;
import com.cotani.event.api.CotaniEvent;
import com.cotani.event.api.EventDispatchPolicy;
import com.cotani.event.api.EventListener;
import com.cotani.event.cancellable.CancellableEvent;
import com.cotani.event.exception.EventExceptionHandler;
import com.cotani.event.exception.EventListenerException;
import com.cotani.event.subscription.EventSubscription;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@InternalApi
public final class DefaultEventDispatcher implements EventDispatcher {
    private final EventExceptionHandler exceptionHandler;
    private final Executor isolatedExecutor;
    private final EventDispatchPolicy policy;

    public DefaultEventDispatcher(EventExceptionHandler exceptionHandler) {
        this(exceptionHandler, Runnable::run, EventDispatchPolicy.defaults());
    }

    public DefaultEventDispatcher(
            EventExceptionHandler exceptionHandler, Executor isolatedExecutor, EventDispatchPolicy policy) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler cannot be null");
        this.isolatedExecutor = Objects.requireNonNull(isolatedExecutor, "isolatedExecutor");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public <T extends CotaniEvent> T dispatch(T event, List<EventSubscription> subscriptions) {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(subscriptions, "subscriptions cannot be null");

        for (EventSubscription subscription : subscriptions) {
            dispatchToSubscription(event, subscription);
        }

        return event;
    }

    @Override
    public <T extends CotaniEvent> CompletionStage<T> dispatchAsync(T event, List<EventSubscription> subscriptions) {
        Objects.requireNonNull(event, "event cannot be null");
        Objects.requireNonNull(subscriptions, "subscriptions cannot be null");

        CompletionStage<Void> sequence = CompletableFuture.completedFuture(null);

        for (var subscription : subscriptions) {
            sequence = sequence.thenCompose(_ -> dispatchToSubscriptionAsync(event, subscription));
        }
        return sequence.thenApply(_ -> event);
    }

    private <T extends CotaniEvent> void dispatchToSubscription(T event, EventSubscription subscription) {
        if (!subscription.active()) {
            return;
        }

        if (subscription.ignoreCancelled()
                && event instanceof CancellableEvent cancellable
                && cancellable.cancelled()) {
            return;
        }

        try {
            listener(subscription).handle(event);
        } catch (Exception exception) {
            exceptionHandler.handle(new EventListenerException(event, subscription, exception));
        }
    }

    private <T extends CotaniEvent> CompletionStage<Void> dispatchToSubscriptionAsync(
            T event, EventSubscription subscription) {
        if (!shouldDispatch(event, subscription)) {
            return CompletableFuture.completedFuture(null);
        }

        var completion = new CompletableFuture<Void>();
        var task = new FutureTask<Void>(() -> {
            try {
                listener(subscription).handle(event);
                completion.complete(null);
            } catch (Exception exception) {
                exceptionHandler.handle(new EventListenerException(event, subscription, exception));
                completion.complete(null);
            } catch (Error error) {
                completion.completeExceptionally(error);
                throw error;
            }

            return null;
        });
        try {
            isolatedExecutor.execute(task);
        } catch (RuntimeException schedulingFailure) {
            completion.completeExceptionally(schedulingFailure);
        }
        long timeoutMillis = timeoutMillis(policy.listenerTimeout());

        return completion.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).exceptionallyCompose(error -> {
            if (!(unwrap(error) instanceof TimeoutException timeout)) {
                return CompletableFuture.failedFuture(error);
            }

            task.cancel(true);
            if (policy.unsubscribeOnTimeout()) {
                subscription.unsubscribe();
            }

            exceptionHandler.handle(new EventListenerException(event, subscription, timeout));

            return CompletableFuture.completedFuture(null);
        });
    }

    private static <T extends CotaniEvent> boolean shouldDispatch(T event, EventSubscription subscription) {
        if (!subscription.active()) {
            return false;
        }

        return !subscription.ignoreCancelled()
                || !(event instanceof CancellableEvent cancellable)
                || !cancellable.cancelled();
    }

    private static long timeoutMillis(Duration timeout) {
        try {
            return Math.max(1L, timeout.toMillis());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;

        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private <T extends CotaniEvent> EventListener<T> listener(EventSubscription subscription) {
        return (EventListener<T>) subscription.listener();
    }
}

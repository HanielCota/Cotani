package com.cotani.task.impl.chain;

import com.cotani.task.api.DelayedTaskScheduler;
import com.cotani.task.api.RetryPolicy;
import com.cotani.task.api.SchedulerTask;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

final class TaskRetryExecutor<T> {
    private final RetryPolicy policy;
    private final DelayedTaskScheduler scheduler;
    private final Supplier<CompletableFuture<T>> attemptFactory;
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private final AtomicReference<SchedulerTask> scheduled = new AtomicReference<>();
    private final AtomicReference<CompletableFuture<T>> active = new AtomicReference<>();

    TaskRetryExecutor(
            RetryPolicy policy, DelayedTaskScheduler scheduler, Supplier<CompletableFuture<T>> attemptFactory) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.attemptFactory = Objects.requireNonNull(attemptFactory, "attemptFactory");
        var _ = result.whenComplete((_, _) -> cancelOwnedWorkIfRequested());
    }

    CompletableFuture<T> execute(CompletableFuture<T> initial) {
        observe(initial, 1);
        return result;
    }

    private void cancelOwnedWorkIfRequested() {
        if (!result.isCancelled()) {
            return;
        }
        SchedulerTask pending = scheduled.getAndSet(null);

        if (pending != null) {
            pending.cancel();
        }
        CompletableFuture<T> running = active.getAndSet(null);

        if (running != null) {
            running.cancel(true);
        }
    }

    private void observe(CompletableFuture<T> attemptFuture, int nextRetryAttempt) {
        Objects.requireNonNull(attemptFuture, "attemptFuture");

        if (result.isDone()) {
            attemptFuture.cancel(true);
            return;
        }

        active.set(attemptFuture);
        var _ = attemptFuture.whenComplete((value, error) -> {
            active.compareAndSet(attemptFuture, null);
            if (result.isDone()) {
                return;
            }
            if (error == null) {
                result.complete(value);
                return;
            }

            Throwable cause = CompletionFailure.unwrap(error);

            if (cause instanceof CancellationException) {
                result.cancel(false);
                return;
            }
            scheduleRetry(cause, nextRetryAttempt);
        });
    }

    private void scheduleRetry(Throwable failure, int retryAttempt) {
        final Duration delay;
        try {
            if (!policy.shouldRetry(retryAttempt, failure)) {
                result.completeExceptionally(failure);
                return;
            }
            delay = Objects.requireNonNull(policy.delayFor(retryAttempt), "retry delay");

            if (delay.isNegative()) {
                throw new IllegalArgumentException("retry delay must not be negative");
            }
        } catch (Throwable policyFailure) {
            addSuppressedIfDistinct(policyFailure, failure);
            result.completeExceptionally(policyFailure);
            return;
        }

        var fired = new AtomicBoolean();
        final SchedulerTask pending;
        try {
            pending = Objects.requireNonNull(
                    scheduler.asyncLater(
                            "chain-retry-" + retryAttempt,
                            () -> {
                                fired.set(true);
                                scheduled.set(null);
                                startRetry(retryAttempt + 1);
                            },
                            delay),
                    "retry scheduler returned null");
        } catch (Throwable schedulingFailure) {
            addSuppressedIfDistinct(schedulingFailure, failure);
            result.completeExceptionally(schedulingFailure);
            return;
        }

        if (fired.get() || result.isDone() || !scheduled.compareAndSet(null, pending)) {
            pending.cancel();
            return;
        }
        if (fired.get()) {
            scheduled.compareAndSet(pending, null);
        }
    }

    private void startRetry(int nextRetryAttempt) {
        if (result.isDone()) {
            return;
        }
        try {
            observe(Objects.requireNonNull(attemptFactory.get(), "retry factory returned null"), nextRetryAttempt);
        } catch (Throwable factoryFailure) {
            scheduleRetry(CompletionFailure.unwrap(factoryFailure), nextRetryAttempt);
        }
    }

    @SuppressWarnings("ReferenceEquality") // Throwable forbids suppressing the same instance.
    private static void addSuppressedIfDistinct(Throwable target, Throwable suppressed) {
        if (target != suppressed) {
            target.addSuppressed(suppressed);
        }
    }
}

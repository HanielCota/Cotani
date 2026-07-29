package com.cotani.task.impl.chain;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.RetryPolicy;
import com.cotani.task.api.SchedulerTask;
import com.cotani.task.api.TaskChain;
import com.cotani.task.exception.TaskTimeoutException;
import com.cotani.task.util.VoidResult;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@com.cotani.api.InternalApi
public final class DefaultTaskChain<T> implements TaskChain<T> {

    private static final String ACTION_PARAM = "action";

    private final CompletableFuture<T> future;
    private final PaperTaskScheduler scheduler;
    private final Supplier<CompletableFuture<T>> futureFactory;
    private final boolean repeatable;

    public DefaultTaskChain(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        this(future, scheduler, () -> future, false);
    }

    public DefaultTaskChain(
            CompletableFuture<T> future, PaperTaskScheduler scheduler, Supplier<CompletableFuture<T>> futureFactory) {
        this(future, scheduler, futureFactory, true);
    }

    private DefaultTaskChain(
            CompletableFuture<T> future,
            PaperTaskScheduler scheduler,
            Supplier<CompletableFuture<T>> futureFactory,
            boolean repeatable) {
        this.future = Objects.requireNonNull(future, "future");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.futureFactory = Objects.requireNonNull(futureFactory, "futureFactory");
        this.repeatable = repeatable;
    }

    @Override
    public <U> TaskChain<U> thenAsync(Function<T, U> function) {
        return thenTarget(ExecutionTarget.async(), "chain-async", function);
    }

    @Override
    public <U> TaskChain<U> thenGlobal(Function<T, U> function) {
        return thenTarget(ExecutionTarget.global(), "chain-global", function);
    }

    @Override
    public <U> TaskChain<U> thenRegion(Location location, Function<T, U> function) {
        // Capture immutable world/chunk ids immediately — never store the live Location.
        return thenTarget(ExecutionTarget.region(location), "chain-region", function);
    }

    @Override
    public <U> TaskChain<U> thenRegion(UUID worldId, int chunkX, int chunkZ, Function<T, U> function) {
        return thenTarget(ExecutionTarget.region(worldId, chunkX, chunkZ), "chain-region", function);
    }

    @Override
    public <U> TaskChain<U> thenEntity(Entity entity, Function<T, U> function) {
        // Capture entity UUID immediately — never store the live Entity reference.
        return thenTarget(ExecutionTarget.entity(entity), "chain-entity", function);
    }

    @Override
    public <U> TaskChain<U> thenEntity(UUID entityId, Function<T, U> function) {
        return thenTarget(ExecutionTarget.entity(entityId), "chain-entity", function);
    }

    @Override
    public TaskChain<T> consumeAsync(Consumer<T> consumer) {
        return consumeTarget(ExecutionTarget.async(), "consume-async", consumer);
    }

    @Override
    public TaskChain<T> consumeGlobal(Consumer<T> consumer) {
        return consumeTarget(ExecutionTarget.global(), "consume-global", consumer);
    }

    @Override
    public TaskChain<T> consumeRegion(Location location, Consumer<T> consumer) {
        return consumeTarget(ExecutionTarget.region(location), "consume-region", consumer);
    }

    @Override
    public TaskChain<T> consumeRegion(UUID worldId, int chunkX, int chunkZ, Consumer<T> consumer) {
        return consumeTarget(ExecutionTarget.region(worldId, chunkX, chunkZ), "consume-region", consumer);
    }

    @Override
    public TaskChain<T> consumeEntity(Entity entity, Consumer<T> consumer) {
        return consumeTarget(ExecutionTarget.entity(entity), "consume-entity", consumer);
    }

    @Override
    public TaskChain<T> consumeEntity(UUID entityId, Consumer<T> consumer) {
        return consumeTarget(ExecutionTarget.entity(entityId), "consume-entity", consumer);
    }

    @Override
    public TaskChain<T> filter(Predicate<T> predicate) {
        Objects.requireNonNull(predicate, "predicate");

        CompletableFuture<T> filtered = future.thenApply(value -> {
            if (predicate.test(value)) {
                return value;
            }

            throw new NoSuchElementException("Value did not match filter predicate");
        });

        Supplier<CompletableFuture<T>> factory = () -> futureFactory.get().thenApply(value -> {
            if (predicate.test(value)) {
                return value;
            }

            throw new NoSuchElementException("Value did not match filter predicate");
        });

        return new DefaultTaskChain<>(filtered, scheduler, factory, repeatable);
    }

    @Override
    public <U> TaskChain<U> flatMap(Function<T, TaskChain<U>> mapper) {
        Objects.requireNonNull(mapper, "mapper");

        CompletableFuture<U> mapped = future.thenCompose(value -> {
            TaskChain<U> inner = mapper.apply(value);

            return inner.toCompletionStage().toCompletableFuture();
        });

        Supplier<CompletableFuture<U>> factory = () -> futureFactory.get().thenCompose(value -> {
            TaskChain<U> inner = mapper.apply(value);

            return inner.toCompletionStage().toCompletableFuture();
        });

        return new DefaultTaskChain<>(mapped, scheduler, factory, repeatable);
    }

    @Override
    public TaskChain<T> timeout(Duration duration) {
        long timeoutNanos = validateTimeout(duration);
        CompletableFuture<T> timed = withTimeout(future, duration, timeoutNanos);
        Supplier<CompletableFuture<T>> factory = () -> withTimeout(futureFactory.get(), duration, timeoutNanos);

        return new DefaultTaskChain<>(timed, scheduler, factory, repeatable);
    }

    @Override
    public TaskChain<T> retry(RetryPolicy retryPolicy) {
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (!repeatable) {
            throw new IllegalStateException(
                    "Retry requires a repeatable supplier; a chain created from an external CompletionStage cannot be retried");
        }

        CompletableFuture<T> retried = retryFuture(future, retryPolicy);

        Supplier<CompletableFuture<T>> factory = () -> retryFuture(futureFactory.get(), retryPolicy);

        return new DefaultTaskChain<>(retried, scheduler, factory, true);
    }

    @Override
    public TaskChain<T> onStart(Runnable action) {
        Objects.requireNonNull(action, ACTION_PARAM);

        CompletableFuture<T> started = CompletableFuture.supplyAsync(
                        () -> {
                            action.run();
                            return VoidResult.nullValue();
                        },
                        scheduler.asyncExecutor())
                .thenCompose(ignored -> future);

        Supplier<CompletableFuture<T>> factory = () -> CompletableFuture.supplyAsync(
                        () -> {
                            action.run();
                            return VoidResult.nullValue();
                        },
                        scheduler.asyncExecutor())
                .thenCompose(ignored -> futureFactory.get());

        return new DefaultTaskChain<>(started, scheduler, factory, repeatable);
    }

    @Override
    public TaskChain<T> onComplete(Runnable action) {
        Objects.requireNonNull(action, ACTION_PARAM);

        var _ = future.whenComplete((ignored, throwable) -> action.run());

        return this;
    }

    @Override
    public TaskChain<T> onCancel(Runnable action) {
        Objects.requireNonNull(action, ACTION_PARAM);

        var _ = future.whenComplete((ignored, throwable) -> {
            if (future.isCancelled()) {
                action.run();
            }
        });

        return this;
    }

    @Override
    public TaskChain<T> onError(Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");

        var handled = future.whenComplete((ignored, throwable) -> {
            if (throwable == null) {
                return;
            }

            consumer.accept(unwrap(throwable));
        });

        return new DefaultTaskChain<>(handled, scheduler, futureFactory, repeatable);
    }

    @Override
    public CompletionStage<T> toCompletionStage() {
        return future;
    }

    @Override
    public boolean cancel() {
        return future.cancel(true);
    }

    private <U> TaskChain<U> thenTarget(ExecutionTarget target, String name, Function<T, U> function) {
        var next = future.thenCompose(value -> scheduler.supply(target, name, () -> function.apply(value)));

        Supplier<CompletableFuture<U>> factory = () ->
                futureFactory.get().thenCompose(value -> scheduler.supply(target, name, () -> function.apply(value)));

        return new DefaultTaskChain<>(next, scheduler, factory, repeatable);
    }

    private TaskChain<T> consumeTarget(ExecutionTarget target, String name, Consumer<T> consumer) {
        var next = future.thenCompose(value -> scheduler.supply(target, name, () -> {
            consumer.accept(value);

            return value;
        }));

        Supplier<CompletableFuture<T>> factory = () -> futureFactory
                .get()
                .thenCompose(value -> scheduler.supply(target, name, () -> {
                    consumer.accept(value);

                    return value;
                }));

        return new DefaultTaskChain<>(next, scheduler, factory, repeatable);
    }

    private static long validateTimeout(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        if (!duration.isPositive()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("duration is too large", overflow);
        }
    }

    private CompletableFuture<T> withTimeout(CompletableFuture<T> source, Duration duration, long timeoutNanos) {
        return source.copy().orTimeout(timeoutNanos, TimeUnit.NANOSECONDS).exceptionallyCompose(throwable -> {
            Throwable cause = unwrap(throwable);
            if (cause instanceof TimeoutException) {
                return CompletableFuture.failedFuture(new TaskTimeoutException(duration));
            }
            return CompletableFuture.failedFuture(cause);
        });
    }

    private CompletableFuture<T> retryFuture(CompletableFuture<T> initial, RetryPolicy policy) {
        var execution = new RetryExecution(policy);
        execution.observe(initial, 1);
        return execution.result;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private final class RetryExecution {

        private final RetryPolicy policy;
        private final CompletableFuture<T> result = new CompletableFuture<>();
        private final AtomicReference<SchedulerTask> scheduled = new AtomicReference<>();
        private final AtomicReference<CompletableFuture<T>> active = new AtomicReference<>();

        private RetryExecution(RetryPolicy policy) {
            this.policy = policy;
            var _ = result.whenComplete((_, _) -> {
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
            });
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

                Throwable cause = unwrap(error);
                if (cause instanceof CancellationException) {
                    result.cancel(false);
                    return;
                }
                scheduleRetry(cause, nextRetryAttempt);
            });
        }

        private void scheduleRetry(Throwable failure, int retryAttempt) {
            final boolean shouldRetry;
            final Duration delay;
            try {
                shouldRetry = policy.shouldRetry(retryAttempt, failure);
                if (!shouldRetry) {
                    result.completeExceptionally(failure);
                    return;
                }
                delay = Objects.requireNonNull(policy.delayFor(retryAttempt), "retry delay");
                if (delay.isNegative()) {
                    throw new IllegalArgumentException("retry delay must not be negative");
                }
            } catch (Throwable policyFailure) {
                policyFailure.addSuppressed(failure);
                result.completeExceptionally(policyFailure);
                return;
            }

            var fired = new AtomicBoolean();
            final SchedulerTask pending;
            try {
                pending = scheduler.asyncLater(
                        "chain-retry-" + retryAttempt,
                        () -> {
                            fired.set(true);
                            scheduled.set(null);
                            startRetry(retryAttempt + 1);
                        },
                        delay);
            } catch (Throwable schedulingFailure) {
                schedulingFailure.addSuppressed(failure);
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
                observe(Objects.requireNonNull(futureFactory.get(), "retry factory returned null"), nextRetryAttempt);
            } catch (Throwable factoryFailure) {
                scheduleRetry(unwrap(factoryFailure), nextRetryAttempt);
            }
        }
    }
}

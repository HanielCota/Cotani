package com.cotani.task.impl.chain;

import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.RetryPolicy;
import com.cotani.task.api.TaskChain;
import com.cotani.task.exception.TaskTimeoutException;
import com.cotani.task.util.VoidResult;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public final class DefaultTaskChain<T> implements TaskChain<T> {

    private static final String ACTION_PARAM = "action";

    private final CompletableFuture<T> future;
    private final PaperTaskScheduler scheduler;
    private final Supplier<CompletableFuture<T>> futureFactory;

    public DefaultTaskChain(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        this(future, scheduler, () -> future);
    }

    private DefaultTaskChain(
            CompletableFuture<T> future, PaperTaskScheduler scheduler, Supplier<CompletableFuture<T>> futureFactory) {
        this.future = Objects.requireNonNull(future, "future");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.futureFactory = Objects.requireNonNull(futureFactory, "futureFactory");
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
        return thenTarget(ExecutionTarget.region(location), "chain-region", function);
    }

    @Override
    public <U> TaskChain<U> thenRegion(UUID worldId, int chunkX, int chunkZ, Function<T, U> function) {
        return thenTarget(ExecutionTarget.region(worldId, chunkX, chunkZ), "chain-region", function);
    }

    @Override
    public <U> TaskChain<U> thenEntity(Entity entity, Function<T, U> function) {
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

        return new DefaultTaskChain<>(filtered, scheduler, factory);
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

        return new DefaultTaskChain<>(mapped, scheduler, factory);
    }

    @Override
    public TaskChain<T> timeout(Duration duration) {
        Objects.requireNonNull(duration, "duration");

        CompletableFuture<T> timed = future.orTimeout(duration.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    throw new CompletionException(new TaskTimeoutException(duration));
                });

        Supplier<CompletableFuture<T>> factory = () -> futureFactory
                .get()
                .orTimeout(duration.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(throwable -> {
                    throw new CompletionException(new TaskTimeoutException(duration));
                });

        return new DefaultTaskChain<>(timed, scheduler, factory);
    }

    @Override
    public TaskChain<T> retry(RetryPolicy retryPolicy) {
        Objects.requireNonNull(retryPolicy, "retryPolicy");

        CompletableFuture<T> retried =
                future.exceptionallyCompose(throwable -> retry(unwrap(throwable), retryPolicy, 1));

        return new DefaultTaskChain<>(retried, scheduler, futureFactory);
    }

    @Override
    public TaskChain<T> onStart(Runnable action) {
        Objects.requireNonNull(action, ACTION_PARAM);

        CompletableFuture<T> started = scheduler
                .supplyAsync("chain-on-start", () -> {
                    action.run();

                    return VoidResult.nullValue();
                })
                .toCompletionStage()
                .thenCompose(ignored -> future)
                .toCompletableFuture();

        Supplier<CompletableFuture<T>> factory = () -> scheduler
                .supplyAsync("chain-on-start", () -> {
                    action.run();

                    return VoidResult.nullValue();
                })
                .toCompletionStage()
                .thenCompose(ignored -> futureFactory.get())
                .toCompletableFuture();

        return new DefaultTaskChain<>(started, scheduler, factory);
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

        return new DefaultTaskChain<>(handled, scheduler, futureFactory);
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

        return new DefaultTaskChain<>(next, scheduler, factory);
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

        return new DefaultTaskChain<>(next, scheduler, factory);
    }

    private CompletableFuture<T> retry(Throwable throwable, RetryPolicy policy, int attempt) {
        if (!policy.shouldRetry(attempt, throwable)) {
            return CompletableFuture.failedFuture(throwable);
        }

        Duration delay = policy.delayFor(attempt);
        CompletableFuture<T> retryFuture = new CompletableFuture<>();

        scheduler.asyncLater(
                () -> {
                    var _ = futureFactory.get().whenComplete((result, error) -> {
                        if (error != null) {
                            retryFuture.completeExceptionally(error);

                            return;
                        }

                        retryFuture.complete(result);
                    });
                },
                delay);

        return retryFuture.exceptionallyCompose(nextThrowable -> retry(unwrap(nextThrowable), policy, attempt + 1));
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }

        return throwable;
    }
}

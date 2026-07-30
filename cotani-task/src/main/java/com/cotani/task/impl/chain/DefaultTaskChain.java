package com.cotani.task.impl.chain;

import com.cotani.api.InternalApi;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.api.RetryPolicy;
import com.cotani.task.api.TaskChain;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

@InternalApi
public final class DefaultTaskChain<T> implements TaskChain<T> {

    private static final String ACTION_PARAM = "action";

    private final ChainState<T> state;
    private final ChainExecutionContext executionContext;
    private final ChainTargetComposer targetComposer;
    private final TaskTimeoutController timeoutController;

    private DefaultTaskChain(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        this(ChainState.external(future), new ChainExecutionContext(scheduler, scheduler));
    }

    private DefaultTaskChain(
            CompletableFuture<T> future, PaperTaskScheduler scheduler, Supplier<CompletableFuture<T>> futureFactory) {
        this(ChainState.repeatable(future, futureFactory), new ChainExecutionContext(scheduler, scheduler));
    }

    private DefaultTaskChain(ChainState<T> state, ChainExecutionContext executionContext) {
        this.state = Objects.requireNonNull(state, "state");
        this.executionContext = Objects.requireNonNull(executionContext, "executionContext");
        this.targetComposer = new ChainTargetComposer(executionContext.executor());
        this.timeoutController = new TaskTimeoutController();
    }

    public static <T> DefaultTaskChain<T> create(CompletableFuture<T> future, PaperTaskScheduler scheduler) {
        return new DefaultTaskChain<>(future, scheduler);
    }

    public static <T> DefaultTaskChain<T> create(
            CompletableFuture<T> future, PaperTaskScheduler scheduler, Supplier<CompletableFuture<T>> futureFactory) {
        return new DefaultTaskChain<>(future, scheduler, futureFactory);
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

        CompletableFuture<T> filtered = state.future().thenApply(value -> {
            if (predicate.test(value)) {
                return value;
            }

            throw new NoSuchElementException("Value did not match filter predicate");
        });

        Supplier<CompletableFuture<T>> factory = () -> state.newAttempt().thenApply(value -> {
            if (predicate.test(value)) {
                return value;
            }

            throw new NoSuchElementException("Value did not match filter predicate");
        });

        return new DefaultTaskChain<>(state.derive(filtered, factory), executionContext);
    }

    @Override
    public <U> TaskChain<U> flatMap(Function<T, TaskChain<U>> mapper) {
        Objects.requireNonNull(mapper, "mapper");

        CompletableFuture<U> mapped = state.future().thenCompose(value -> {
            TaskChain<U> inner = mapper.apply(value);

            return inner.toCompletionStage().toCompletableFuture();
        });

        Supplier<CompletableFuture<U>> factory = () -> state.newAttempt().thenCompose(value -> {
            TaskChain<U> inner = mapper.apply(value);

            return inner.toCompletionStage().toCompletableFuture();
        });

        return new DefaultTaskChain<>(state.derive(mapped, factory), executionContext);
    }

    @Override
    public TaskChain<T> timeout(Duration duration) {
        CompletableFuture<T> timed = timeoutController.apply(state.future(), duration);
        Supplier<CompletableFuture<T>> factory = () -> timeoutController.apply(state.newAttempt(), duration);
        return new DefaultTaskChain<>(state.derive(timed, factory), executionContext);
    }

    @Override
    public TaskChain<T> retry(RetryPolicy retryPolicy) {
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (!state.repeatable()) {
            throw new IllegalStateException(
                    "Retry requires a repeatable supplier; a chain created from an external CompletionStage cannot be retried");
        }

        CompletableFuture<T> retried = retry(state.future(), retryPolicy);
        Supplier<CompletableFuture<T>> factory = () -> retry(state.newAttempt(), retryPolicy);
        return new DefaultTaskChain<>(state.derive(retried, factory), executionContext);
    }

    @Override
    public TaskChain<T> onStart(Runnable action) {
        Objects.requireNonNull(action, ACTION_PARAM);
        return new DefaultTaskChain<>(
                ChainLifecycleCallbacks.onStart(
                        state, action, executionContext.executor().asyncExecutor()),
                executionContext);
    }

    @Override
    public TaskChain<T> onComplete(Runnable action) {
        ChainLifecycleCallbacks.onComplete(state.future(), Objects.requireNonNull(action, ACTION_PARAM));
        return this;
    }

    @Override
    public TaskChain<T> onCancel(Runnable action) {
        ChainLifecycleCallbacks.onCancel(state.future(), Objects.requireNonNull(action, ACTION_PARAM));
        return this;
    }

    @Override
    public TaskChain<T> onError(Consumer<Throwable> consumer) {
        return new DefaultTaskChain<>(ChainLifecycleCallbacks.onError(state, consumer), executionContext);
    }

    @Override
    public CompletionStage<T> toCompletionStage() {
        return state.future();
    }

    @Override
    public boolean cancel() {
        return state.future().cancel(true);
    }

    private <U> TaskChain<U> thenTarget(ExecutionTarget target, String name, Function<T, U> function) {
        return new DefaultTaskChain<>(targetComposer.thenTarget(state, target, name, function), executionContext);
    }

    private TaskChain<T> consumeTarget(ExecutionTarget target, String name, Consumer<T> consumer) {
        return new DefaultTaskChain<>(targetComposer.consumeTarget(state, target, name, consumer), executionContext);
    }

    private CompletableFuture<T> retry(CompletableFuture<T> initial, RetryPolicy retryPolicy) {
        return new TaskRetryExecutor<>(retryPolicy, executionContext.delays(), state::newAttempt).execute(initial);
    }
}

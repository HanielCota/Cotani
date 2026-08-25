package com.cotani.task.internal.chain;

import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.ExecutionTarget;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class ChainTargetComposer {
    private final AsyncTaskExecutor scheduler;

    ChainTargetComposer(AsyncTaskExecutor scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    <T, U> ChainState<U> thenTarget(ChainState<T> state, ExecutionTarget target, String name, Function<T, U> function) {
        CompletableFuture<U> next =
                state.future().thenCompose(value -> scheduler.supply(target, name, () -> function.apply(value)));
        Supplier<CompletableFuture<U>> factory = () ->
                state.newAttempt().thenCompose(value -> scheduler.supply(target, name, () -> function.apply(value)));

        return state.derive(next, factory);
    }

    <T> ChainState<T> consumeTarget(ChainState<T> state, ExecutionTarget target, String name, Consumer<T> consumer) {
        CompletableFuture<T> next = state.future()
                .thenCompose(value -> scheduler.supply(target, name, () -> {
                    consumer.accept(value);
                    return value;
                }));
        Supplier<CompletableFuture<T>> factory = () -> state.newAttempt()
                .thenCompose(value -> scheduler.supply(target, name, () -> {
                    consumer.accept(value);
                    return value;
                }));

        return state.derive(next, factory);
    }
}

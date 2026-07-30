package com.cotani.task.impl.chain;

import com.cotani.task.util.VoidResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class ChainLifecycleCallbacks {

    private ChainLifecycleCallbacks() {}

    static <T> ChainState<T> onStart(ChainState<T> state, Runnable action, Executor executor) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(executor, "executor");
        CompletableFuture<T> started = runStart(action, executor).thenCompose(_ -> state.future());
        Supplier<CompletableFuture<T>> factory =
                () -> runStart(action, executor).thenCompose(_ -> state.newAttempt());
        return state.derive(started, factory);
    }

    static <T> void onComplete(CompletableFuture<T> future, Runnable action) {
        Objects.requireNonNull(action, "action");
        var _ = future.whenComplete((_, _) -> action.run());
    }

    static <T> void onCancel(CompletableFuture<T> future, Runnable action) {
        Objects.requireNonNull(action, "action");
        var _ = future.whenComplete((_, _) -> {
            if (future.isCancelled()) {
                action.run();
            }
        });
    }

    static <T> ChainState<T> onError(ChainState<T> state, Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<T> handled = state.future().whenComplete((_, throwable) -> {
            if (throwable != null) {
                consumer.accept(CompletionFailure.unwrap(throwable));
            }
        });
        return state.derive(handled, state::newAttempt);
    }

    private static CompletableFuture<Void> runStart(Runnable action, Executor executor) {
        return CompletableFuture.supplyAsync(
                () -> {
                    action.run();
                    return VoidResult.nullValue();
                },
                executor);
    }
}

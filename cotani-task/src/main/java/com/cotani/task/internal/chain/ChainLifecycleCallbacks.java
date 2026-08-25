package com.cotani.task.internal.chain;

import com.cotani.task.util.VoidResult;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ChainLifecycleCallbacks {
    private static final Logger LOGGER = Logger.getLogger(ChainLifecycleCallbacks.class.getName());

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

        var _ = future.whenComplete((_, _) -> runCallback(action));
    }

    static <T> void onCancel(CompletableFuture<T> future, Runnable action) {
        Objects.requireNonNull(action, "action");

        var _ = future.whenComplete((_, _) -> {
            if (future.isCancelled()) {
                runCallback(action);
            }
        });
    }

    static <T> ChainState<T> onError(ChainState<T> state, Consumer<Throwable> consumer) {
        Objects.requireNonNull(consumer, "consumer");

        CompletableFuture<T> handled = state.future().whenComplete((_, throwable) -> {
            if (throwable != null) {
                try {
                    consumer.accept(CompletionFailure.unwrap(throwable));
                } catch (Exception callbackFailure) {
                    LOGGER.log(Level.SEVERE, "Task chain error callback failed", callbackFailure);
                }
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

    private static void runCallback(Runnable action) {
        try {
            action.run();
        } catch (Exception callbackFailure) {
            LOGGER.log(Level.SEVERE, "Task chain lifecycle callback failed", callbackFailure);
        }
    }
}

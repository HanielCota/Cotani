package com.cotani.task.impl.chain;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class ChainState<T> {

    private final CompletableFuture<T> future;
    private final Supplier<CompletableFuture<T>> futureFactory;
    private final boolean repeatable;

    private ChainState(CompletableFuture<T> future, Supplier<CompletableFuture<T>> futureFactory, boolean repeatable) {
        this.future = Objects.requireNonNull(future, "future");
        this.futureFactory = Objects.requireNonNull(futureFactory, "futureFactory");
        this.repeatable = repeatable;
    }

    static <T> ChainState<T> external(CompletableFuture<T> future) {
        return new ChainState<>(future, () -> future, false);
    }

    static <T> ChainState<T> repeatable(CompletableFuture<T> future, Supplier<CompletableFuture<T>> futureFactory) {
        return new ChainState<>(future, futureFactory, true);
    }

    CompletableFuture<T> future() {
        return future;
    }

    CompletableFuture<T> newAttempt() {
        return Objects.requireNonNull(futureFactory.get(), "chain factory returned null");
    }

    boolean repeatable() {
        return repeatable;
    }

    <U> ChainState<U> derive(CompletableFuture<U> derivedFuture, Supplier<CompletableFuture<U>> derivedFactory) {
        return new ChainState<>(derivedFuture, derivedFactory, repeatable);
    }
}

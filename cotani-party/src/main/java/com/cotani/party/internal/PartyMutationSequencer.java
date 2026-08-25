package com.cotani.party.internal;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Serializes party mutations and owns their close lifecycle. */
@NullMarked
final class PartyMutationSequencer {
    private final Object lock;
    private final AtomicBoolean closed = new AtomicBoolean();

    private CompletionStage<Void> sequencingTail = completedVoid();
    private CompletionStage<Void> lastOperation = completedVoid();
    private @Nullable CompletionStage<Void> closeStage;

    PartyMutationSequencer(Object lock) {
        this.lock = Objects.requireNonNull(lock, "lock");
    }

    boolean isClosed() {
        return closed.get();
    }

    <T> CompletionStage<T> submit(
            Supplier<CompletionStage<T>> operation, Supplier<? extends RuntimeException> closedFailure) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(closedFailure, "closedFailure");

        synchronized (lock) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(closedFailure.get());
            }

            var submitted = sequencingTail.handle((ignored, failure) -> null).thenCompose(ignored -> {
                try {
                    return Objects.requireNonNull(operation.get(), "operation stage");
                } catch (RuntimeException failure) {
                    return CompletableFuture.failedFuture(failure);
                }
            });
            sequencingTail = submitted.handle((ignored, failure) -> null);
            lastOperation = submitted.thenApply(ignored -> null);
            return submitted;
        }
    }

    CompletionStage<Void> close(Runnable cleanup) {
        Objects.requireNonNull(cleanup, "cleanup");

        synchronized (lock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            closeStage = lastOperation.whenComplete((ignored, failure) -> cleanup.run());
            return closeStage;
        }
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }
}

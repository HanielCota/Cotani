package com.cotani.cache.internal.caffeine;

import com.cotani.task.util.CompletionStages;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

final class BoundedAsyncWorkCoordinator<T> {

    private final List<T> items;
    private final int workerCount;
    private final Function<T, CompletionStage<Void>> operation;
    private final AtomicLong nextIndex = new AtomicLong();
    private final AtomicInteger remainingWorkers;
    private final AtomicReference<@Nullable Throwable> firstFailure = new AtomicReference<>();
    private final CompletableFuture<Void> result = new CompletableFuture<>();

    BoundedAsyncWorkCoordinator(List<T> items, int maximumConcurrency, Function<T, CompletionStage<Void>> operation) {
        this.items = List.copyOf(items);
        if (maximumConcurrency <= 0) {
            throw new IllegalArgumentException("maximumConcurrency must be positive");
        }
        this.workerCount = Math.min(maximumConcurrency, items.size());
        this.operation = Objects.requireNonNull(operation, "operation");
        this.remainingWorkers = new AtomicInteger(workerCount);
    }

    CompletionStage<Void> start() {
        if (items.isEmpty()) {
            return CompletionStages.completedVoid();
        }
        for (int worker = 0; worker < workerCount; worker++) {
            advance();
        }
        return result;
    }

    private void advance() {
        while (advanceSynchronously()) {
            // Keep consuming already-completed work without recursive callbacks.
        }
    }

    private boolean advanceSynchronously() {
        long index = nextIndex.getAndIncrement();
        if (index >= items.size()) {
            workerFinished();
            return false;
        }
        var future = invoke(items.get(Math.toIntExact(index)));
        if (future.isDone()) {
            recordCompletedFuture(future);
            return true;
        }
        var _ = future.whenComplete((_, error) -> {
            if (error != null) {
                recordFailure(error);
            }
            advance();
        });
        return false;
    }

    private CompletableFuture<Void> invoke(T item) {
        return CompletableFuture.completedFuture(item)
                .thenCompose(
                        current -> Objects.requireNonNull(operation.apply(current), "bulk operation returned null"))
                .toCompletableFuture();
    }

    private void recordCompletedFuture(CompletableFuture<Void> future) {
        try {
            future.getNow(null);
        } catch (CompletionException | CancellationException failure) {
            recordFailure(failure.getCause() == null ? failure : failure.getCause());
        }
    }

    private void recordFailure(Throwable failure) {
        Throwable previous = firstFailure.get();
        if (previous == null) {
            if (firstFailure.compareAndSet(null, failure)) {
                return;
            }
            previous = firstFailure.get();
        }
        if (previous != null && !previous.equals(failure)) {
            previous.addSuppressed(failure);
        }
    }

    private void workerFinished() {
        if (remainingWorkers.decrementAndGet() != 0) {
            return;
        }
        Throwable failure = firstFailure.get();
        if (failure == null) {
            result.complete(null);
        } else {
            result.completeExceptionally(failure);
        }
    }
}

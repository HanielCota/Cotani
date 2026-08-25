package com.cotani.achievement.internal;

import com.cotani.achievement.api.AchievementId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Serializes mutations per key and bounds accepted work without blocking callers. */
final class AchievementMutationQueue {
    private final int maxPendingMutations;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lock = new Object();
    private final Map<Key, CompletionStage<Void>> sequencingTails = new HashMap<>();
    private int pendingMutationCount;
    private @Nullable CompletableFuture<Void> closeStage;

    AchievementMutationQueue(int maxPendingMutations) {
        if (maxPendingMutations <= 0) {
            throw new IllegalArgumentException("maxPendingMutations must be positive");
        }
        this.maxPendingMutations = maxPendingMutations;
    }

    <T> CompletionStage<T> await(Key key, Supplier<CompletionStage<T>> operation) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(operation, "operation");
        CompletionStage<Void> predecessor;
        synchronized (lock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            predecessor = sequencingTails.getOrDefault(key, completedVoid());
        }
        return predecessor.thenCompose(
                ignored -> Objects.requireNonNull(operation.get(), "operation returned null stage"));
    }

    <T> CompletionStage<T> enqueue(Supplier<Mutation<T>> operation, Key key) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(key, "key");
        synchronized (lock) {
            if (closed.get()) {
                return failed(closedFailure());
            }
            if (pendingMutationCount >= maxPendingMutations) {
                return failed(new RejectedExecutionException("Achievement mutation queue is full"));
            }
            pendingMutationCount++;
            var result = new CompletableFuture<T>();
            var barrier = new CompletableFuture<Void>();
            var completion = barrier.whenComplete((ignored, failure) -> decrementPendingMutationCount());
            Objects.requireNonNull(completion, "completion");

            var predecessor = sequencingTails.getOrDefault(key, completedVoid());
            predecessor.whenComplete((ignored, ignoredFailure) -> start(operation, result, barrier));

            CompletionStage<Void> nextTail = barrier.handle((ignored, ignoredFailure) -> (Void) null);
            sequencingTails.put(key, nextTail);
            nextTail.whenComplete((ignored, ignoredFailure) -> removeTail(key, nextTail));
            return result;
        }
    }

    CompletionStage<Void> closeAsync() {
        synchronized (lock) {
            if (closeStage != null) {
                return closeStage;
            }
            closed.set(true);
            var pending = sequencingTails.values().stream()
                    .map(CompletionStage::toCompletableFuture)
                    .toArray(CompletableFuture[]::new);
            closeStage = CompletableFuture.allOf(pending);
            return closeStage;
        }
    }

    private <T> void start(
            Supplier<Mutation<T>> operation, CompletableFuture<T> result, CompletableFuture<Void> barrier) {
        Mutation<T> mutation;
        try {
            mutation = Objects.requireNonNull(operation.get(), "operation");
        } catch (RuntimeException failure) {
            result.completeExceptionally(failure);
            barrier.completeExceptionally(failure);
            return;
        }

        mutation.result().whenComplete((value, failure) -> {
            if (failure == null) {
                result.complete(value);
            } else {
                result.completeExceptionally(failure);
            }
        });
        mutation.barrier().whenComplete((ignored, failure) -> {
            if (failure == null) {
                barrier.complete(null);
            } else {
                barrier.completeExceptionally(failure);
            }
        });
    }

    private void decrementPendingMutationCount() {
        synchronized (lock) {
            pendingMutationCount--;
        }
    }

    private void removeTail(Key key, CompletionStage<Void> tail) {
        synchronized (lock) {
            if (Objects.equals(sequencingTails.get(key), tail)) {
                sequencingTails.remove(key);
            }
        }
    }

    private static IllegalStateException closedFailure() {
        return new IllegalStateException("Achievement service is closed");
    }

    private static <T> CompletionStage<T> failed(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }

    private static CompletionStage<Void> completedVoid() {
        return CompletableFuture.completedFuture(null);
    }

    record Key(UUID playerId, AchievementId achievementId) {
        Key {
            Objects.requireNonNull(playerId, "playerId");
            Objects.requireNonNull(achievementId, "achievementId");
        }
    }

    record Mutation<T>(CompletionStage<T> result, CompletionStage<Void> barrier) {
        Mutation {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(barrier, "barrier");
        }
    }
}

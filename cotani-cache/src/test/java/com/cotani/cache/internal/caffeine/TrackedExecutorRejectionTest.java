package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class TrackedExecutorRejectionTest {
    @Test
    void rejectedTaskDoesNotBlockIdleCompletion() {
        var executor = new TrackedExecutor(_ -> {
            throw new RejectedExecutionException("executor closed");
        });

        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
        assertTrue(executor.whenIdle().toCompletableFuture().isDone());
    }

    @Test
    void nullCommandRejects() {
        var executor = new TrackedExecutor(Runnable::run);

        assertThrows(NullPointerException.class, () -> executor.execute(null));
        assertTrue(executor.whenIdle().toCompletableFuture().isDone());
    }

    @Test
    void rejectedTaskBetweenAcceptedTasksKeepsIdleTrackingConsistent() {
        var tasks = new ArrayDeque<Runnable>();
        var accepted = new AtomicInteger();
        var executor = new TrackedExecutor(command -> {
            if (accepted.incrementAndGet() == 2) {
                throw new RejectedExecutionException("full");
            }
            tasks.add(command);
        });

        executor.execute(() -> {});
        assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> {}));
        executor.execute(() -> {});
        var idle = executor.whenIdle();

        assertFalse(idle.toCompletableFuture().isDone());
        tasks.remove().run();
        assertFalse(idle.toCompletableFuture().isDone());
        tasks.remove().run();
        assertTrue(idle.toCompletableFuture().isDone());
    }
}

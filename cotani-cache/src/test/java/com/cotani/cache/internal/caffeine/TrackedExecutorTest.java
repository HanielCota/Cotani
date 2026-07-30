package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;

class TrackedExecutorTest {

    @Test
    void idleCompletionWaitsForEveryAcceptedTask() {
        var tasks = new ArrayDeque<Runnable>();
        var executor = new TrackedExecutor(tasks::add);

        executor.execute(() -> {});
        executor.execute(() -> {});
        var idle = executor.whenIdle();

        assertFalse(idle.toCompletableFuture().isDone());
        tasks.remove().run();
        assertFalse(idle.toCompletableFuture().isDone());
        tasks.remove().run();
        assertTrue(idle.toCompletableFuture().isDone());
    }

    @Test
    void directExecutorFailureDecrementsActiveTaskOnlyOnce() {
        var executor = new TrackedExecutor(Runnable::run);

        assertThrows(
                IllegalStateException.class,
                () -> executor.execute(() -> {
                    throw new IllegalStateException("task failed");
                }));

        assertTrue(executor.whenIdle().toCompletableFuture().isDone());
        executor.execute(() -> {});
        assertTrue(executor.whenIdle().toCompletableFuture().isDone());
    }
}

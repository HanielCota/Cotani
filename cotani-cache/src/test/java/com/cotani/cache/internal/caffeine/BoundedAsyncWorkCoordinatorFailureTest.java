package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class BoundedAsyncWorkCoordinatorFailureTest {
    @Test
    void singleFailureCompletesResultExceptionally() {
        var failure = new IllegalStateException("boom");
        var coordinator =
                new BoundedAsyncWorkCoordinator<>(List.of("a"), 1, _ -> CompletableFuture.failedFuture(failure));

        var thrown = assertThrows(
                ExecutionException.class,
                () -> coordinator.start().toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertSame(failure, thrown.getCause());
    }

    @Test
    void multipleFailuresSurfaceFirstAndSuppressOthers() {
        var firstFailure = new IllegalStateException("first");
        var secondFailure = new IllegalStateException("second");
        var coordinator = new BoundedAsyncWorkCoordinator<>(
                List.of(1, 2),
                1,
                item -> item == 1
                        ? CompletableFuture.failedFuture(firstFailure)
                        : CompletableFuture.failedFuture(secondFailure));

        var thrown = assertThrows(
                ExecutionException.class,
                () -> coordinator.start().toCompletableFuture().get(5, TimeUnit.SECONDS));

        assertSame(firstFailure, thrown.getCause());
        assertTrue(Arrays.asList(thrown.getCause().getSuppressed()).contains(secondFailure));
    }

    @Test
    void failureInOneItemDoesNotPreventOthersFromRunning() {
        var executed = new AtomicInteger();
        var coordinator = new BoundedAsyncWorkCoordinator<>(List.of(1, 2), 1, item -> {
            executed.incrementAndGet();

            if (item == 1) {
                return CompletableFuture.failedFuture(new IllegalStateException("boom"));
            }

            return CompletableFuture.completedFuture(null);
        });

        assertThrows(
                ExecutionException.class,
                () -> coordinator.start().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertEquals(2, executed.get());
    }

    @Test
    void constructorRejectsNonPositiveConcurrency() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedAsyncWorkCoordinator<>(List.of("a"), 0, _ -> CompletableFuture.completedFuture(null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BoundedAsyncWorkCoordinator<>(
                        List.of("a"), -1, _ -> CompletableFuture.completedFuture(null)));
    }
}

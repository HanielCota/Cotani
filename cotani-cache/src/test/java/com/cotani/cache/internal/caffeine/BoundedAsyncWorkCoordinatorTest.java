package com.cotani.cache.internal.caffeine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BoundedAsyncWorkCoordinatorTest {
    @Test
    void neverAdmitsMoreThanConfiguredConcurrency() {
        var active = new AtomicInteger();
        var peak = new AtomicInteger();
        var admitted = new ArrayList<CompletableFuture<Void>>();
        var coordinator = new BoundedAsyncWorkCoordinator<>(List.of(1, 2, 3, 4, 5, 6), 2, _ -> {
            int current = active.incrementAndGet();
            peak.accumulateAndGet(current, Math::max);
            var pending = new CompletableFuture<Void>();
            admitted.add(pending);

            return pending.whenComplete((_, _) -> active.decrementAndGet());
        });

        var result = coordinator.start();

        assertEquals(2, admitted.size());
        assertFalse(result.toCompletableFuture().isDone());
        for (int index = 0; index < 6; index++) {
            admitted.get(index).complete(null);
        }

        assertEquals(2, peak.get());
        assertEquals(0, active.get());
        assertTrue(result.toCompletableFuture().isDone());
    }

    @Test
    void emptyBatchCompletesWithoutStartingWorkers() {
        var coordinator = new BoundedAsyncWorkCoordinator<>(List.<Integer>of(), 1, _ -> {
            throw new AssertionError("operation must not run");
        });

        assertTrue(coordinator.start().toCompletableFuture().isDone());
    }
}

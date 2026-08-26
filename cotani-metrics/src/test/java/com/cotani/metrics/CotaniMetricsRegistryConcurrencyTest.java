package com.cotani.metrics;

import static org.junit.jupiter.api.Assertions.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Verifies that {@link CotaniMetricsRegistry} never loses counter updates under real
 * concurrency.
 */
class CotaniMetricsRegistryConcurrencyTest {

    private static final int THREADS = 8;
    private static final int INCREMENTS_PER_THREAD = 1_000;
    private static final int ROUNDS = 10;

    @Test
    void shouldNotLoseUpdatesUnderConcurrency() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            runConcurrentIncrementRound(round);
        }
    }

    private void runConcurrentIncrementRound(int round) throws Exception {
        MeterRegistry simpleRegistry = new SimpleMeterRegistry();
        CotaniMetricsRegistry registry = new CotaniMetricsRegistry(simpleRegistry, "app");
        String roundTag = "round-" + round;
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Void>> tasks = new ArrayList<>();

        try {
            for (int thread = 0; thread < THREADS; thread++) {
                tasks.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("start signal not released in time");
                    }
                    for (int i = 0; i < INCREMENTS_PER_THREAD; i++) {
                        registry.counter("events", "round", roundTag);
                    }
                    return null;
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "workers did not become ready in time");
            start.countDown();

            for (Future<Void> task : tasks) {
                assertNull(task.get(5, TimeUnit.SECONDS), "worker failed");
            }
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS), "workers did not terminate in time");
        }

        Counter counter =
                simpleRegistry.find("app.events").tag("round", roundTag).counter();
        assertNotNull(counter, "counter was not registered");
        assertEquals((double) THREADS * INCREMENTS_PER_THREAD, counter.count(), 0.0, "lost updates in round " + round);

        registry.close();
    }
}

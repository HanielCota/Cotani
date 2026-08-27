package com.cotani.storage.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.testkit.StressTestSupport;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

class AdmissionControlledExecutorServiceTest {
    @Test
    @Tag("stress")
    void executesThousandsOfAdmittedStorageOperationsWithoutLoss() {
        var completed = new AtomicInteger();
        try (var executor = AdmissionControlledExecutorService.create(
                Executors.newVirtualThreadPerTaskExecutor(), 32, StressTestSupport.iterations())) {
            var operations = new CompletableFuture<?>[StressTestSupport.iterations()];
            for (int index = 0; index < operations.length; index++) {
                operations[index] = CompletableFuture.runAsync(completed::incrementAndGet, executor);
            }
            CompletableFuture.allOf(operations).join();

            assertEquals(StressTestSupport.iterations(), completed.get());
            assertEquals(0, executor.activeOperations());
            assertEquals(0, executor.queuedOperations());
            assertEquals(0, executor.rejectedOperations());
        }
    }

    @Test
    void boundsActiveWorkAndQueueWithoutRunningOnCaller() throws InterruptedException {
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(2);
        try (var executor =
                AdmissionControlledExecutorService.create(Executors.newVirtualThreadPerTaskExecutor(), 2, 1)) {
            Runnable blocked = () -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            };

            executor.execute(blocked);
            executor.execute(blocked);
            executor.execute(blocked);

            Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                assertThrows(RejectedExecutionException.class, () -> executor.execute(blocked));
            });
            started.await(1, TimeUnit.SECONDS);
            assertEquals(2, executor.activeOperations());
            assertEquals(1, executor.queuedOperations());
            assertEquals(1, executor.rejectedOperations());
            release.countDown();
        } finally {
            release.countDown();
        }
    }
}

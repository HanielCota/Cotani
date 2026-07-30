package com.cotani.storage.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AdmissionControlledExecutorServiceTest {

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

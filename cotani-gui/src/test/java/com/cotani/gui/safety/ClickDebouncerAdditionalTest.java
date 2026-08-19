package com.cotani.gui.safety;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Extra {@link ClickDebouncer} scenarios not covered by {@link ClickDebouncerTest}: zero-duration
 * debounce, per-player isolation and release for unknown players.
 */
final class ClickDebouncerAdditionalTest {
    @Test
    void shouldAcceptConsecutiveClicksWithZeroDebounce() {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ZERO);
        UUID playerId = UUID.randomUUID();

        assertTrue(debouncer.tryAcquire(playerId));
        assertTrue(debouncer.tryAcquire(playerId));
    }

    @Test
    void shouldDebounceIndependentlyPerPlayer() {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ofMinutes(1));
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        assertTrue(debouncer.tryAcquire(player1));
        assertTrue(debouncer.tryAcquire(player2));
        assertFalse(debouncer.tryAcquire(player1));
        assertFalse(debouncer.tryAcquire(player2));
    }

    @Test
    void shouldTreatReleaseForUnknownPlayerAsNoOp() {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> debouncer.release(UUID.randomUUID()));
        assertTrue(debouncer.tryAcquire(UUID.randomUUID()));
    }

    @Test
    void shouldAtomicallyDebounceUnderConcurrentContention() throws InterruptedException {
        ClickDebouncer debouncer = new ClickDebouncer(Duration.ofMinutes(1));
        UUID playerId = UUID.randomUUID();
        int threads = 16;
        var executor = java.util.concurrent.Executors.newFixedThreadPool(threads);
        var latch = new java.util.concurrent.CountDownLatch(threads);
        var successCount = new java.util.concurrent.atomic.AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                if (debouncer.tryAcquire(playerId)) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS));
        executor.shutdown();
        assertEquals(1, successCount.get());
    }
}

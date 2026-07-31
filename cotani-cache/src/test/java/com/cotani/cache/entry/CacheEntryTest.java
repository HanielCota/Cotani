package com.cotani.cache.entry;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

@SuppressWarnings("NullAway")
class CacheEntryTest {
    @Test
    void valueReturnsInitialValue() {
        CacheEntry<String> entry = CacheEntry.of("hello");

        assertEquals("hello", entry.value());
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(NullPointerException.class, () -> CacheEntry.of(null));
    }

    @Test
    void updateReturnsBecameDirty() {
        CacheEntry<String> entry = CacheEntry.of("old");

        assertTrue(entry.update(v -> v + "-new"));
        assertEquals("old-new", entry.value());
    }

    @Test
    void updateOnAlreadyDirtyEntryDoesNotReportBecameDirty() {
        CacheEntry<String> entry = CacheEntry.of("value");
        entry.markDirty();

        assertFalse(entry.update(v -> v + "-new"));
        assertTrue(entry.dirty());
    }

    @Test
    void updateRejectsNullResult() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertThrows(NullPointerException.class, () -> entry.update(v -> null));
    }

    @Test
    void updateRejectsNullUpdater() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertThrows(NullPointerException.class, () -> entry.update(null));
    }

    @Test
    void mutateReturnsBecameDirty() {
        CacheEntry<StringBuilder> entry = CacheEntry.of(new StringBuilder("hello"));

        assertTrue(entry.mutate(sb -> sb.append(" world")));
        assertEquals("hello world", entry.value().toString());
    }

    @Test
    void mutateOnAlreadyDirtyEntryDoesNotReportBecameDirty() {
        CacheEntry<StringBuilder> entry = CacheEntry.of(new StringBuilder("hello"));
        entry.markDirty();

        assertFalse(entry.mutate(sb -> sb.append(" world")));
        assertTrue(entry.dirty());
    }

    @Test
    void mutateRejectsNullMutator() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertThrows(NullPointerException.class, () -> entry.mutate(null));
    }

    @Test
    void dirtyReturnsFalseInitially() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertFalse(entry.dirty());
    }

    @Test
    void markDirtySetsDirtyFlagAndReturnsBecameDirty() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertTrue(entry.markDirty());
        assertTrue(entry.dirty());
    }

    @Test
    void markDirtyOnAlreadyDirtyEntryDoesNotReportBecameDirty() {
        CacheEntry<String> entry = CacheEntry.of("value");
        entry.markDirty();

        assertFalse(entry.markDirty());
        assertTrue(entry.dirty());
    }

    @Test
    void updateSetsDirtyFlag() {
        CacheEntry<String> entry = CacheEntry.of("value");

        entry.update(v -> v + "-updated");

        assertTrue(entry.dirty());
    }

    @Test
    void mutateSetsDirtyFlag() {
        CacheEntry<StringBuilder> entry = CacheEntry.of(new StringBuilder("hello"));

        entry.mutate(sb -> sb.append(" world"));

        assertTrue(entry.dirty());
    }

    @Test
    void markSavedClearsDirtyFlag() {
        CacheEntry<String> entry = CacheEntry.of("value");

        entry.markDirty();
        assertTrue(entry.dirty());

        entry.markSaved();
        assertFalse(entry.dirty());
    }

    @Test
    void loadedAtIsSetOnCreation() {
        Instant before = Instant.now();
        CacheEntry<String> entry = CacheEntry.of("value");
        Instant after = Instant.now();

        assertFalse(entry.loadedAt().isBefore(before));
        assertFalse(entry.loadedAt().isAfter(after));
    }

    @Test
    void lastSavedAtIsEmptyInitially() {
        CacheEntry<String> entry = CacheEntry.of("value");

        Optional<Instant> lastSaved = entry.lastSavedAt();

        assertTrue(lastSaved.isEmpty());
    }

    @Test
    void lastSavedAtIsPresentAfterMarkSaved() {
        CacheEntry<String> entry = CacheEntry.of("value");

        entry.markSaved();
        Optional<Instant> lastSaved = entry.lastSavedAt();

        assertTrue(lastSaved.isPresent());
    }

    @Test
    void dirtyFlagSurvivesMultipleUpdates() {
        CacheEntry<String> entry = CacheEntry.of("a");

        assertTrue(entry.update(v -> v + "b"));
        assertFalse(entry.update(v -> v + "c"));

        assertTrue(entry.dirty());
        assertEquals("abc", entry.value());
    }

    @Test
    void versionIncrementsOnUpdateAndMarkDirty() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertEquals(0L, entry.version());

        entry.update(v -> v + "-1");
        assertEquals(1L, entry.version());

        entry.markDirty();
        assertEquals(2L, entry.version());
    }

    @Test
    void markSavedIfVersionMatchesClearsDirtyWhenVersionMatches() {
        CacheEntry<String> entry = CacheEntry.of("value");
        entry.markDirty();
        long versionAtStart = entry.version();

        assertTrue(entry.markSavedIfVersionMatches(versionAtStart));
        assertFalse(entry.dirty());
    }

    @Test
    void markSavedIfVersionMatchesReturnsFalseWhenVersionChanged() {
        CacheEntry<String> entry = CacheEntry.of("value");
        entry.markDirty();
        long versionAtStart = entry.version();

        entry.update(v -> v + "-new");

        assertFalse(entry.markSavedIfVersionMatches(versionAtStart));
        assertTrue(entry.dirty());
    }

    @Test
    void markSavedIfVersionMatchesReturnsFalseWhenAlreadyClean() {
        CacheEntry<String> entry = CacheEntry.of("value");

        assertFalse(entry.markSavedIfVersionMatches(entry.version()));
    }

    @Test
    void mutableUpdaterExecutesExactlyOncePerConcurrentOperation() throws InterruptedException {
        CacheEntry<AtomicInteger> entry = CacheEntry.of(new AtomicInteger());
        AtomicInteger invocations = new AtomicInteger();
        int threads = 8;
        int operationsPerThread = 1_000;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        var executor = Executors.newFixedThreadPool(threads);

        try {
            for (int thread = 0; thread < threads; thread++) {
                executor.execute(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        for (int operation = 0; operation < operationsPerThread; operation++) {
                            entry.mutate(value -> {
                                invocations.incrementAndGet();
                                value.incrementAndGet();
                            });
                        }
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        int expected = threads * operationsPerThread;
        assertEquals(expected, invocations.get());
        assertEquals(expected, entry.value().get());
        assertEquals(expected, entry.version());
    }
}

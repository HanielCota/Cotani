package com.cotani.cooldown.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownTargets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryCooldownStoreTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final CooldownKey KEY = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));

    @Test
    void shouldSaveAndFindEntry() {
        var store = new InMemoryCooldownStore();
        var entry = new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(5)));

        store.save(entry);

        assertEquals(entry, store.find(KEY).orElseThrow());
        assertEquals(1L, store.estimatedSize());
        assertTrue(store.find(new CooldownKey(CooldownTargets.global(), CooldownAction.of("other")))
                .isEmpty());
    }

    @Test
    void shouldTreatEqualKeysAsTheSameEntry() {
        var store = new InMemoryCooldownStore();
        var first = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));
        var second = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));

        store.save(new CooldownEntry(first, NOW, NOW.plus(Duration.ofSeconds(5))));

        assertTrue(store.find(second).isPresent());
    }

    @Test
    void shouldOverwriteEntryForTheSameKey() {
        var store = new InMemoryCooldownStore();
        var replaced = new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(5)));
        var replacement = new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(10)));

        store.save(replaced);
        store.save(replacement);

        assertEquals(replacement, store.find(KEY).orElseThrow());
        assertEquals(1L, store.estimatedSize());
    }

    @Test
    void shouldRemoveEntry() {
        var store = new InMemoryCooldownStore();
        store.save(new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(5))));

        store.remove(KEY);

        assertTrue(store.find(KEY).isEmpty());
    }

    @Test
    void shouldClearAllEntries() {
        var store = new InMemoryCooldownStore();
        var otherKey = new CooldownKey(CooldownTargets.global(), CooldownAction.of("other"));
        store.save(new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(5))));
        store.save(new CooldownEntry(otherKey, NOW, NOW.plus(Duration.ofSeconds(5))));

        store.clear();

        assertEquals(0L, store.estimatedSize());
        assertTrue(store.find(KEY).isEmpty());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);

        assertThrows(NullPointerException.class, () -> store.find(null));
        assertThrows(NullPointerException.class, () -> store.remove(null));
        assertThrows(NullPointerException.class, () -> store.removeExpired(null));
        assertThrows(NullPointerException.class, () -> store.checkAndStart(null, Duration.ofSeconds(5), clock));
        assertThrows(NullPointerException.class, () -> store.checkAndStart(KEY, null, clock));
        assertThrows(NullPointerException.class, () -> store.checkAndStart(KEY, Duration.ofSeconds(5), null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullEntryOnSave() {
        var store = new InMemoryCooldownStore();

        assertThrows(NullPointerException.class, () -> store.save(null));
    }

    @Test
    void shouldRejectNonPositiveDuration() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);

        assertThrows(IllegalArgumentException.class, () -> store.checkAndStart(KEY, Duration.ZERO, clock));
        assertThrows(IllegalArgumentException.class, () -> store.checkAndStart(KEY, Duration.ofSeconds(-1), clock));
    }

    @Test
    void shouldAllowCheckAndStartWhenNoCooldownIsActive() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);

        var result = store.checkAndStart(KEY, Duration.ofSeconds(5), clock);

        assertTrue(result.allowed());
        assertEquals(Duration.ZERO, result.remaining());
        assertTrue(store.find(KEY).isPresent());
    }

    @Test
    void shouldDenyCheckAndStartWhileCooldownIsActive() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);
        store.checkAndStart(KEY, Duration.ofSeconds(5), clock);

        var result = store.checkAndStart(KEY, Duration.ofSeconds(5), clock);

        assertTrue(result.denied());
        assertEquals(Duration.ofSeconds(5), result.remaining());
        assertEquals(NOW.plus(Duration.ofSeconds(5)), result.expiresAtOptional().orElseThrow());
    }

    @Test
    void shouldAllowCheckAndStartAgainAfterExpiry() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);
        store.checkAndStart(KEY, Duration.ofSeconds(5), clock);
        clock.advance(Duration.ofSeconds(6));

        var result = store.checkAndStart(KEY, Duration.ofSeconds(5), clock);

        assertTrue(result.allowed());
        var stored = store.find(KEY).orElseThrow();
        assertEquals(clock.instant(), stored.startedAt());
    }

    @Test
    void shouldRemoveExpiredEntriesOnDemand() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);
        store.save(new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(1))));
        clock.advance(Duration.ofSeconds(2));

        store.removeExpired(clock);

        assertTrue(store.find(KEY).isEmpty());
    }

    @Test
    void shouldKeepExpiredEntriesUntilFindPurges() {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);
        store.save(new CooldownEntry(KEY, NOW, NOW.plus(Duration.ofSeconds(1))));
        clock.advance(Duration.ofSeconds(2));

        assertTrue(store.find(KEY).isPresent());
    }

    @Test
    void shouldAllowOnlyOneWinnerUnderConcurrentCheckAndStart() throws InterruptedException {
        var store = new InMemoryCooldownStore();
        var clock = new MutableClock(NOW);
        int callers = 16;
        var ready = new CountDownLatch(callers);
        var start = new CountDownLatch(1);
        var done = new CountDownLatch(callers);
        var allowed = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(callers);

        try {
            for (int i = 0; i < callers; i++) {
                var _ = executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        if (store.checkAndStart(KEY, Duration.ofMinutes(1), clock)
                                .allowed()) {
                            allowed.incrementAndGet();
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
            assertTrue(done.await(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, allowed.get());
        assertFalse(store.find(KEY).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

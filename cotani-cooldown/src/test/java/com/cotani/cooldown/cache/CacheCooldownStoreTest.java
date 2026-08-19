package com.cotani.cooldown.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownTargets;
import com.cotani.cooldown.api.UserCooldownTarget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CacheCooldownStoreTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final CooldownKey GLOBAL_KEY = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreGlobalCooldown() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);

        var first = store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);
        var second = store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);

        assertTrue(first.allowed());
        assertTrue(second.denied());
        assertEquals(Duration.ofSeconds(5), second.remaining());
        assertTrue(store.find(GLOBAL_KEY).isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldStoreResourceCooldownSeparately() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);
        var spawnKey = new CooldownKey(CooldownTargets.resource("spawn"), CooldownAction.of("use"));
        var netherKey = new CooldownKey(CooldownTargets.resource("nether"), CooldownAction.of("use"));

        assertTrue(store.checkAndStart(spawnKey, Duration.ofSeconds(5), clock).allowed());
        assertTrue(store.checkAndStart(netherKey, Duration.ofSeconds(5), clock).allowed());
        assertTrue(store.checkAndStart(spawnKey, Duration.ofSeconds(5), clock).denied());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveNonPlayerCooldown() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);
        store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);

        store.remove(GLOBAL_KEY);

        assertTrue(store.find(GLOBAL_KEY).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldClearNonPlayerCooldowns() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);
        var resourceKey = new CooldownKey(CooldownTargets.resource("spawn"), CooldownAction.of("use"));
        store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);
        store.checkAndStart(resourceKey, Duration.ofSeconds(5), clock);

        store.clear();

        assertTrue(store.find(GLOBAL_KEY).isEmpty());
        assertTrue(store.find(resourceKey).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRemoveExpiredNonPlayerCooldowns() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);
        store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);
        clock.advance(Duration.ofSeconds(6));

        store.removeExpired(clock);

        assertTrue(store.find(GLOBAL_KEY).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowNonPlayerCooldownAfterExpiry() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);
        store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);
        clock.advance(Duration.ofSeconds(6));

        var result = store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), clock);

        assertTrue(result.allowed());
    }

    @Test
    @SuppressWarnings({"unchecked", "NullAway"})
    void shouldRejectNonPositiveDuration() {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
        var clock = new MutableClock(NOW);

        assertThrows(IllegalArgumentException.class, () -> store.checkAndStart(GLOBAL_KEY, Duration.ZERO, clock));
        assertThrows(
                IllegalArgumentException.class, () -> store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(-1), clock));
        assertThrows(NullPointerException.class, () -> store.checkAndStart(GLOBAL_KEY, Duration.ofSeconds(5), null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldThrowWhenUserIsNotLoadedOnCheckAndStart() {
        var playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        when(playerCache.find(userId)).thenReturn(Optional.empty());
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("use"));

        assertThrows(
                IllegalStateException.class,
                () -> store.checkAndStart(key, Duration.ofSeconds(5), new MutableClock(NOW)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipSaveWhenUserIsNotLoaded() {
        var playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        when(playerCache.find(userId)).thenReturn(Optional.empty());
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("use"));
        var entry = new CooldownEntry(key, NOW, NOW.plusSeconds(5));

        store.save(entry);

        verify(playerCache, never()).markDirty(userId);
        assertTrue(store.find(key).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldFindEmptyForUnloadedUser() {
        var playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        when(playerCache.find(userId)).thenReturn(Optional.empty());
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("use"));

        assertTrue(store.find(key).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSkipRemoveWhenUserIsNotLoaded() {
        var playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        when(playerCache.find(userId)).thenReturn(Optional.empty());
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("use"));

        store.remove(key);

        verify(playerCache, never()).markDirty(userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPersistUserCooldownThroughThePlayerCache() {
        var playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        var playerCooldowns = new PlayerCooldowns(userId);
        when(playerCache.find(userId)).thenReturn(Optional.of(playerCooldowns));
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("use"));

        store.save(new CooldownEntry(key, NOW, NOW.plusSeconds(5)));

        verify(playerCache).markDirty(userId);
        verify(playerCache).mutateAsync(eq(userId), any());
        assertTrue(playerCooldowns.find("use").isPresent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldMarkDirtyOnlyWhenUserCooldownStarts() {
        var playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        var playerCooldowns = new PlayerCooldowns(userId);
        when(playerCache.find(userId)).thenReturn(Optional.of(playerCooldowns));
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("use"));
        var clock = new MutableClock(NOW);

        assertTrue(store.checkAndStart(key, Duration.ofSeconds(5), clock).allowed());
        assertTrue(store.checkAndStart(key, Duration.ofSeconds(5), clock).denied());

        verify(playerCache, times(1)).markDirty(userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllowOnlyOneNonPlayerWinnerUnderConcurrentCheckAndStart() throws InterruptedException {
        var playerCache = mock(PlayerDataCache.class);
        var store = new CacheCooldownStore(playerCache);
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
                        if (store.checkAndStart(GLOBAL_KEY, Duration.ofMinutes(1), clock)
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

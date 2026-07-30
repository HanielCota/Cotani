package com.cotani.cooldown;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.cotani.cache.api.PlayerDataCache;
import com.cotani.cooldown.api.*;
import com.cotani.cooldown.cache.CacheCooldownStore;
import com.cotani.cooldown.cache.PlayerCooldowns;
import com.cotani.cooldown.internal.*;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CooldownServiceTest {

    @Test
    void testInMemoryCooldown() {
        CooldownService cooldownService = DefaultCooldownService.inMemory();
        UUID userId = UUID.randomUUID();
        String action = "test.action";
        Duration duration = Duration.ofSeconds(5);

        CooldownResult result1 =
                cooldownService.user(userId).action(action).duration(duration).checkAndStart();

        assertTrue(result1.allowed());
        assertFalse(result1.denied());

        CooldownResult result2 =
                cooldownService.user(userId).action(action).duration(duration).checkAndStart();

        assertFalse(result2.allowed());
        assertTrue(result2.denied());
        assertTrue(result2.remaining().toMillis() > 0);
        assertTrue(result2.expiresAtOptional().isPresent());

        assertTrue(cooldownService.deny(userId, action, duration));
        assertFalse(cooldownService.allow(userId, action, duration));
    }

    @Test
    void testCooldownExpiration() {
        MutableClock clock = new MutableClock(Instant.now());
        CooldownStore store = new InMemoryCooldownStore();
        CooldownService cooldownService = new DefaultCooldownService(store, clock);
        UUID userId = UUID.randomUUID();
        String action = "test.expiring";
        Duration duration = Duration.ofSeconds(5);

        CooldownResult result1 =
                cooldownService.user(userId).action(action).duration(duration).checkAndStart();

        assertTrue(result1.allowed());

        clock.advance(Duration.ofSeconds(6));

        CooldownResult result2 =
                cooldownService.user(userId).action(action).duration(duration).checkAndStart();

        assertTrue(result2.allowed());
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCacheCooldownStore() {
        PlayerDataCache<PlayerCooldowns> playerCache = mock(PlayerDataCache.class);
        UUID userId = UUID.randomUUID();
        PlayerCooldowns playerCooldowns = new PlayerCooldowns(userId);

        when(playerCache.get(userId)).thenReturn(playerCooldowns);
        when(playerCache.find(userId)).thenReturn(Optional.of(playerCooldowns));

        CooldownStore store = new CacheCooldownStore(playerCache);
        CooldownService service = new DefaultCooldownService(store, Clock.systemUTC());

        assertTrue(service.user(userId)
                .action("action")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .allowed());
        assertTrue(service.user(userId)
                .action("action")
                .duration(Duration.ofSeconds(5))
                .checkAndStart()
                .denied());

        verify(playerCache, atLeastOnce()).markDirty(userId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cachedPlayerCooldownCheckAndStartIsAtomic() throws InterruptedException {
        PlayerDataCache<PlayerCooldowns> playerCache = mock(PlayerDataCache.class);
        var userId = UUID.randomUUID();
        var playerCooldowns = new PlayerCooldowns(userId);
        when(playerCache.find(userId)).thenReturn(Optional.of(playerCooldowns));
        var store = new CacheCooldownStore(playerCache);
        var key = new CooldownKey(new UserCooldownTarget(userId), CooldownAction.of("atomic"));
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"));
        int callers = 32;
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
                        if (store.checkAndStart(key, Duration.ofSeconds(30), clock)
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

        Assertions.assertEquals(1, allowed.get());
        verify(playerCache, times(1)).markDirty(userId);
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
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

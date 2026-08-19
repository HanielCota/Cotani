package com.cotani.cooldown.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownEntry;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownResult;
import com.cotani.cooldown.api.UserCooldownTarget;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PlayerCooldownsTest {
    private static final UUID PLAYER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final CooldownKey KEY = new CooldownKey(new UserCooldownTarget(PLAYER_ID), CooldownAction.of("use"));

    @Test
    void shouldExposePlayerIdAndEmptyCooldowns() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);

        assertEquals(PLAYER_ID, playerCooldowns.playerId());
        assertTrue(playerCooldowns.activeCooldowns().isEmpty());
    }

    @Test
    void shouldPutAndFindByActionName() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
        var entry = entry(Duration.ofSeconds(5));

        playerCooldowns.put(entry);

        assertEquals(entry, playerCooldowns.find("use").orElseThrow());
        assertTrue(playerCooldowns.find("other").isEmpty());
    }

    @Test
    void shouldRemoveByActionName() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
        playerCooldowns.put(entry(Duration.ofSeconds(5)));

        playerCooldowns.remove("use");

        assertTrue(playerCooldowns.find("use").isEmpty());
    }

    @Test
    void shouldTrackIndependentActionsSeparately() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
        var otherKey = new CooldownKey(new UserCooldownTarget(PLAYER_ID), CooldownAction.of("other"));

        playerCooldowns.put(entry(Duration.ofSeconds(5)));

        var result = playerCooldowns.checkAndStart(otherKey, Duration.ofSeconds(5), NOW);

        assertTrue(result.allowed());
        assertTrue(playerCooldowns.find("use").isPresent());
        assertTrue(playerCooldowns.find("other").isPresent());
    }

    @Test
    void shouldReturnImmutableSnapshotOfActiveCooldowns() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
        playerCooldowns.put(entry(Duration.ofSeconds(5)));

        assertThrows(
                UnsupportedOperationException.class,
                () -> playerCooldowns.activeCooldowns().put("other", entry(Duration.ofSeconds(5))));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArguments() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);

        assertThrows(NullPointerException.class, () -> new PlayerCooldowns(null, new ConcurrentHashMap<>()));
        assertThrows(NullPointerException.class, () -> playerCooldowns.put(null));
        assertThrows(NullPointerException.class, () -> playerCooldowns.find(null));
        assertThrows(NullPointerException.class, () -> playerCooldowns.remove(null));
        assertThrows(NullPointerException.class, () -> playerCooldowns.checkAndStart(null, Duration.ofSeconds(5), NOW));
        assertThrows(NullPointerException.class, () -> playerCooldowns.checkAndStart(KEY, null, NOW));
        assertThrows(NullPointerException.class, () -> playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(5), null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullActiveCooldownsMap() {
        assertThrows(NullPointerException.class, () -> new PlayerCooldowns(PLAYER_ID, null));
    }

    @Test
    void shouldAllowCheckAndStartWhenNoCooldownIsActive() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);

        var result = playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(5), NOW);

        assertTrue(result.allowed());
        assertEquals(entry(Duration.ofSeconds(5)), playerCooldowns.find("use").orElseThrow());
    }

    @Test
    void shouldDenyWhileCooldownIsActive() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
        playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(5), NOW);

        var result = playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(5), NOW);

        assertTrue(result.denied());
        assertEquals(Duration.ofSeconds(5), result.remaining());
        assertEquals(NOW.plus(Duration.ofSeconds(5)), result.expiresAtOptional().orElseThrow());
    }

    @Test
    void shouldAllowAgainAfterExpiry() {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
        playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(5), NOW);

        var result = playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(5), NOW.plusSeconds(6));

        assertTrue(result.allowed());
    }

    @Test
    void shouldAllowOnlyOneWinnerUnderConcurrentCheckAndStart() throws InterruptedException {
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID);
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
                        CooldownResult result = playerCooldowns.checkAndStart(KEY, Duration.ofSeconds(30), NOW);
                        if (result.allowed()) {
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
        assertFalse(playerCooldowns.find("use").isEmpty());
    }

    @Test
    void shouldIgnoreMapValuesProvidedByCaller() {
        var entries = new ConcurrentHashMap<String, CooldownEntry>();
        entries.put("use", entry(Duration.ofSeconds(5)));
        var playerCooldowns = new PlayerCooldowns(PLAYER_ID, entries);

        assertTrue(playerCooldowns.find("use").isPresent());
        assertEquals(Map.of("use", entry(Duration.ofSeconds(5))), playerCooldowns.activeCooldowns());
    }

    private static CooldownEntry entry(Duration duration) {
        return new CooldownEntry(KEY, NOW, NOW.plus(duration));
    }
}

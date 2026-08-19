package com.cotani.cooldown.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CooldownEntryTest {
    private static final Instant STARTED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final CooldownKey KEY = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));

    @Test
    void shouldExposeComponents() {
        var expiresAt = STARTED_AT.plus(Duration.ofSeconds(5));
        var entry = new CooldownEntry(KEY, STARTED_AT, expiresAt);

        assertEquals(KEY, entry.key());
        assertEquals(STARTED_AT, entry.startedAt());
        assertEquals(expiresAt, entry.expiresAt());
    }

    @Test
    void shouldRejectExpiryBeforeOrAtStart() {
        assertThrows(IllegalArgumentException.class, () -> new CooldownEntry(KEY, STARTED_AT, STARTED_AT));
        assertThrows(
                IllegalArgumentException.class, () -> new CooldownEntry(KEY, STARTED_AT, STARTED_AT.minusSeconds(1)));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullComponents() {
        var expiresAt = STARTED_AT.plus(Duration.ofSeconds(5));

        assertThrows(NullPointerException.class, () -> new CooldownEntry(null, STARTED_AT, expiresAt));
        assertThrows(NullPointerException.class, () -> new CooldownEntry(KEY, null, expiresAt));
        assertThrows(NullPointerException.class, () -> new CooldownEntry(KEY, STARTED_AT, null));
    }

    @Test
    void shouldReportExpiredAtBoundary() {
        var entry = entry(Duration.ofSeconds(5));

        assertTrue(entry.expired(entry.expiresAt()));
        assertFalse(entry.expired(entry.expiresAt().minusNanos(1)));
    }

    @Test
    void shouldReturnZeroRemainingWhenExpired() {
        var entry = entry(Duration.ofSeconds(5));

        assertEquals(Duration.ZERO, entry.remaining(entry.expiresAt()));
        assertEquals(Duration.ZERO, entry.remaining(entry.expiresAt().plusSeconds(10)));
    }

    @Test
    void shouldComputeRemainingWhenActive() {
        var entry = entry(Duration.ofSeconds(5));

        assertEquals(Duration.ofSeconds(5), entry.remaining(STARTED_AT));
        assertEquals(Duration.ofSeconds(2), entry.remaining(STARTED_AT.plusSeconds(3)));
    }

    @Test
    void shouldWorkWithClock() {
        var entry = entry(Duration.ofSeconds(5));
        var clock = Clock.fixed(STARTED_AT.plusSeconds(1), ZoneOffset.UTC);

        assertFalse(entry.expired(clock));
        assertEquals(Duration.ofSeconds(4), entry.remaining(clock));
    }

    @Test
    void shouldUseValueSemantics() {
        var first = entry(Duration.ofSeconds(5));
        var second = entry(Duration.ofSeconds(5));
        var other = entry(Duration.ofSeconds(10));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullNowWhenCheckingExpiry() {
        var entry = entry(Duration.ofSeconds(5));

        assertThrows(NullPointerException.class, () -> entry.expired((Instant) null));
        assertThrows(NullPointerException.class, () -> entry.expired((Clock) null));
        assertThrows(NullPointerException.class, () -> entry.remaining((Instant) null));
        assertThrows(NullPointerException.class, () -> entry.remaining((Clock) null));
    }

    private static CooldownEntry entry(Duration duration) {
        return new CooldownEntry(KEY, STARTED_AT, STARTED_AT.plus(duration));
    }
}

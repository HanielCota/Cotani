package com.cotani.cooldown.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CooldownResultTest {
    private static final CooldownKey KEY = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));

    @Test
    void shouldCreateAllowedResult() {
        var result = CooldownResult.allowed(KEY);

        assertEquals(CooldownState.ALLOWED, result.state());
        assertTrue(result.allowed());
        assertFalse(result.denied());
        assertEquals(Duration.ZERO, result.remaining());
        assertEquals(Optional.empty(), result.expiresAtOptional());
    }

    @Test
    void shouldCreateDeniedResult() {
        var expiresAt = Instant.parse("2026-01-01T00:00:10Z");
        var result = CooldownResult.denied(KEY, Duration.ofSeconds(5), expiresAt);

        assertEquals(CooldownState.DENIED, result.state());
        assertTrue(result.denied());
        assertFalse(result.allowed());
        assertEquals(Duration.ofSeconds(5), result.remaining());
        assertEquals(Optional.of(expiresAt), result.expiresAtOptional());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullState() {
        assertThrows(NullPointerException.class, () -> new CooldownResult(null, KEY, Duration.ZERO, null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullKey() {
        assertThrows(
                NullPointerException.class, () -> new CooldownResult(CooldownState.ALLOWED, null, Duration.ZERO, null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullRemaining() {
        assertThrows(NullPointerException.class, () -> new CooldownResult(CooldownState.ALLOWED, KEY, null, null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullExpiresAtInDeniedFactory() {
        assertThrows(NullPointerException.class, () -> CooldownResult.denied(KEY, Duration.ofSeconds(5), null));
    }

    @Test
    void shouldUseValueSemantics() {
        var expiresAt = Instant.parse("2026-01-01T00:00:10Z");
        var first = CooldownResult.denied(KEY, Duration.ofSeconds(5), expiresAt);
        var second = CooldownResult.denied(KEY, Duration.ofSeconds(5), expiresAt);
        var other = CooldownResult.allowed(KEY);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, other);
    }
}

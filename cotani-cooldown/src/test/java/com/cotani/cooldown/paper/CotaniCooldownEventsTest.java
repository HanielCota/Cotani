package com.cotani.cooldown.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.cooldown.api.CooldownAction;
import com.cotani.cooldown.api.CooldownKey;
import com.cotani.cooldown.api.CooldownTargets;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CotaniCooldownEventsTest {
    private static final CooldownKey KEY = new CooldownKey(CooldownTargets.global(), CooldownAction.of("use"));

    @Test
    void shouldExposeStartEventFields() {
        var duration = Duration.ofSeconds(5);
        var event = new CotaniCooldownStartEvent(KEY, duration);

        assertEquals(KEY, event.getKey());
        assertEquals(duration, event.getDuration());
    }

    @Test
    void shouldExposeDenyEventFields() {
        var remaining = Duration.ofSeconds(3);
        var expiresAt = Instant.parse("2026-01-01T00:00:05Z");
        var event = new CotaniCooldownDenyEvent(KEY, remaining, expiresAt);

        assertEquals(KEY, event.getKey());
        assertEquals(remaining, event.getRemaining());
        assertEquals(expiresAt, event.getExpiresAt());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullStartEventArguments() {
        assertThrows(NullPointerException.class, () -> new CotaniCooldownStartEvent(null, Duration.ofSeconds(5)));
        assertThrows(NullPointerException.class, () -> new CotaniCooldownStartEvent(KEY, null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullDenyEventArguments() {
        assertThrows(
                NullPointerException.class,
                () -> new CotaniCooldownDenyEvent(null, Duration.ofSeconds(3), Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> new CotaniCooldownDenyEvent(KEY, null, Instant.EPOCH));
        assertThrows(NullPointerException.class, () -> new CotaniCooldownDenyEvent(KEY, Duration.ofSeconds(3), null));
    }

    @Test
    void shouldExposeHandlerList() {
        var event = new CotaniCooldownStartEvent(KEY, Duration.ofSeconds(5));

        assertSame(CotaniCooldownStartEvent.getHandlerList(), event.getHandlers());
        assertSame(
                CotaniCooldownDenyEvent.getHandlerList(),
                new CotaniCooldownDenyEvent(KEY, Duration.ofSeconds(3), Instant.EPOCH).getHandlers());
    }

    @Test
    void shouldUseDistinctHandlerListsPerEventType() {
        assertNotSame(CotaniCooldownStartEvent.getHandlerList(), CotaniCooldownDenyEvent.getHandlerList());
    }
}

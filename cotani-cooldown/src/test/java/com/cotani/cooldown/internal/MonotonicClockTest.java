package com.cotani.cooldown.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MonotonicClockTest {

    @Test
    void instantNeverMovesBackwardsWithinTheProcess() {
        var clock = new MonotonicClock();

        var first = clock.instant();
        var second = clock.instant();

        assertFalse(second.isBefore(first));
    }

    @Test
    void withZonePreservesClockIdentityAndValueSemantics() {
        var clock = new MonotonicClock();
        var paris = ZoneId.of("Europe/Paris");

        assertSame(clock, clock.withZone(ZoneOffset.UTC));
        assertEquals(paris, clock.withZone(paris).getZone());
        assertEquals(clock, clock.withZone(paris).withZone(ZoneOffset.UTC));
        assertEquals(
                clock.hashCode(), clock.withZone(paris).withZone(ZoneOffset.UTC).hashCode());
    }
}

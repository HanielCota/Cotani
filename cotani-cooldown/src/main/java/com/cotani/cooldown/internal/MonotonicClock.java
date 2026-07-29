package com.cotani.cooldown.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Process-local clock whose elapsed time is not affected by wall-clock adjustments. */
final class MonotonicClock extends Clock {

    private final Instant origin = Instant.now();
    private final long originNanos = System.nanoTime();

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return origin.plusNanos(System.nanoTime() - originNanos);
    }
}

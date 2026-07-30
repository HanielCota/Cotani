package com.cotani.cooldown.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/** Process-local clock whose elapsed time is not affected by wall-clock adjustments. */
final class MonotonicClock extends Clock {

    private final Instant origin;
    private final long originNanos;
    private final ZoneId zone;

    MonotonicClock() {
        this(Instant.now(), System.nanoTime(), ZoneOffset.UTC);
    }

    private MonotonicClock(Instant origin, long originNanos, ZoneId zone) {
        this.origin = origin;
        this.originNanos = originNanos;
        this.zone = zone;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        var requiredZone = Objects.requireNonNull(zone, "zone");
        return this.zone.equals(requiredZone) ? this : new MonotonicClock(origin, originNanos, requiredZone);
    }

    @Override
    public Instant instant() {
        return origin.plusNanos(System.nanoTime() - originNanos);
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || (other instanceof MonotonicClock that
                        && originNanos == that.originNanos
                        && origin.equals(that.origin)
                        && zone.equals(that.zone));
    }

    @Override
    public int hashCode() {
        return Objects.hash(origin, originNanos, zone);
    }
}

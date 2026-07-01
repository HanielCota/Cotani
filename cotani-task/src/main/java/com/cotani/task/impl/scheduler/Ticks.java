package com.cotani.task.impl.scheduler;

import java.time.Duration;
import java.util.Objects;

final class Ticks {

    private static final long MILLIS_PER_TICK = 50L;

    private Ticks() {}

    static long from(Duration duration) {
        Objects.requireNonNull(duration, "duration");

        long millis = Math.max(MILLIS_PER_TICK, duration.toMillis());

        return Math.max(1L, (millis + MILLIS_PER_TICK / 2) / MILLIS_PER_TICK);
    }
}

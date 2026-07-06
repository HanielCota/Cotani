package com.cotani.cooldown;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record CooldownEntry(CooldownKey key, Instant startedAt, Instant expiresAt) {

    public CooldownEntry {
        Objects.requireNonNull(key, "key cannot be null");
        Objects.requireNonNull(startedAt, "startedAt cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");

        if (!expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("expiresAt must be after startedAt");
        }
    }

    public boolean expired(Clock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");

        return !expiresAt.isAfter(clock.instant());
    }

    public Duration remaining(Clock clock) {
        Objects.requireNonNull(clock, "clock cannot be null");

        if (expired(clock)) {
            return Duration.ZERO;
        }

        return Duration.between(clock.instant(), expiresAt);
    }
}

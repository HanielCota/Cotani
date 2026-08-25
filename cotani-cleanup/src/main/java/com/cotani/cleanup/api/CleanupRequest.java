package com.cotani.cleanup.api;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Immutable user intent for one cleanup operation. */
public record CleanupRequest(CleanupRequestId id, CleanupPolicy policy, String reason, Instant requestedAt) {
    public CleanupRequest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (reason.isBlank() || reason.length() > 128) {
            throw new IllegalArgumentException("reason must contain between 1 and 128 characters");
        }
    }

    public static CleanupRequest create(CleanupPolicy policy, String reason) {
        return create(policy, reason, Clock.systemUTC());
    }

    public static CleanupRequest create(CleanupPolicy policy, String reason, Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new CleanupRequest(CleanupRequestId.random(), policy, reason, clock.instant());
    }
}

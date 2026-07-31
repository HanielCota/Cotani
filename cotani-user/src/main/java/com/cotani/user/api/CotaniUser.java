package com.cotani.user.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Public read-only view of a loaded Cotani user.
 */
public interface CotaniUser {
    UUID uniqueId();

    String username();

    long firstJoinAt();

    long lastJoinAt();

    long lastQuitAt();

    default Instant firstJoinInstant() {
        return Instant.ofEpochMilli(firstJoinAt());
    }

    default Instant lastJoinInstant() {
        return Instant.ofEpochMilli(lastJoinAt());
    }

    default Instant lastQuitInstant() {
        return Instant.ofEpochMilli(lastQuitAt());
    }
}

package com.cotani.inventory.api;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

/** Ownership proof for one cross-server inventory transfer lock lease. */
@NullMarked
public record TransferLease(UUID playerId, String token) {
    public TransferLease {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        if (token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
    }
}

package com.cotani.teleport.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface TeleportService {
    CompletionStage<TeleportResult> teleport(TeleportRequest request);

    /** Async-suffixed alias that makes the execution contract explicit at call sites. */
    default CompletionStage<TeleportResult> teleportAsync(TeleportRequest request) {
        return teleport(request);
    }

    default boolean hasIndeterminateTeleport(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return false;
    }

    /**
     * Administratively releases a player whose underlying Paper teleport never completed.
     * Callers acknowledge that a physical late teleport may still occur after this method.
     */
    default boolean releaseIndeterminateTeleport(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return false;
    }
}

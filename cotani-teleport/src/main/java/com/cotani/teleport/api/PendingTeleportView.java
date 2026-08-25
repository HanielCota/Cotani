package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

public record PendingTeleportView(
        UUID id,
        UUID playerId,
        Location target,
        Duration delay,
        PendingTeleportState state,
        @Nullable TeleportCancelReason cancelReasonNullable) {
    public PendingTeleportView {
        java.util.Objects.requireNonNull(id, "id");
        java.util.Objects.requireNonNull(playerId, "playerId");
        java.util.Objects.requireNonNull(target, "target");
        java.util.Objects.requireNonNull(delay, "delay");
        java.util.Objects.requireNonNull(state, "state");
    }

    public Optional<TeleportCancelReason> cancelReason() {
        return Optional.ofNullable(cancelReasonNullable);
    }

    /**
     * Returns a defensive copy of the target location. Callers must not rely on mutating the
     * returned instance to affect the pending teleport.
     */
    @Override
    public Location target() {
        return target.clone();
    }
}

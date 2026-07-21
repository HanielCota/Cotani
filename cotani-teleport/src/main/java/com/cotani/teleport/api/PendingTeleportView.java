package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public record PendingTeleportView(
        UUID id,
        UUID playerId,
        Location target,
        Duration delay,
        PendingTeleportState state,
        Optional<TeleportCancelReason> cancelReason) {

    /**
     * Returns a defensive copy of the target location. Callers must not rely on mutating the
     * returned instance to affect the pending teleport.
     */
    @Override
    public Location target() {
        return target.clone();
    }
}

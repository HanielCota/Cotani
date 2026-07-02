package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;

public interface PendingTeleportService {
    UUID schedule(
            UUID playerId,
            Location target,
            Duration delay,
            TeleportOptions options,
            TeleportCause cause,
            String source);

    boolean cancel(UUID playerId, TeleportCancelReason reason);

    boolean hasPending(UUID playerId);

    Optional<PendingTeleportView> find(UUID playerId);
}

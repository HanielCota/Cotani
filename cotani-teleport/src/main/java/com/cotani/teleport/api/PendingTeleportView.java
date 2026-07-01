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
        Optional<TeleportCancelReason> cancelReason) {}

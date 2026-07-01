package com.cotani.teleport.pending;

import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import java.time.Duration;
import java.util.UUID;
import org.bukkit.Location;

public record PendingTeleportData(
        UUID id,
        UUID playerId,
        Location target,
        Duration delay,
        TeleportOptions options,
        TeleportCause cause,
        String source) {
    public PendingTeleportData {
        target = target.clone();
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }

    @Override
    public Location target() {
        return target.clone();
    }

    public static PendingTeleportData create(
            UUID playerId,
            Location target,
            Duration delay,
            TeleportOptions options,
            TeleportCause cause,
            String source) {
        return new PendingTeleportData(UUID.randomUUID(), playerId, target, delay, options, cause, source);
    }
}

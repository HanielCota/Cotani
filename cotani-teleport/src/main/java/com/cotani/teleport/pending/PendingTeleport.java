package com.cotani.teleport.pending;

import com.cotani.api.InternalApi;
import com.cotani.teleport.api.TeleportCause;
import com.cotani.teleport.api.TeleportOptions;
import java.time.Duration;
import java.util.UUID;
import org.bukkit.Location;

@InternalApi
public record PendingTeleport(
        UUID id,
        UUID playerId,
        Location target,
        Duration delay,
        TeleportOptions options,
        TeleportCause cause,
        String source) {
    public PendingTeleport {
        target = target.clone();

        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }

    public static PendingTeleport create(
            UUID playerId,
            Location target,
            Duration delay,
            TeleportOptions options,
            TeleportCause cause,
            String source) {
        return new PendingTeleport(UUID.randomUUID(), playerId, target, delay, options, cause, source);
    }

    @Override
    public Location target() {
        return target.clone();
    }
}

package com.cotani.teleport.api;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Location;

public record TeleportContext(
        UUID playerId,
        Location from,
        Location target,
        TeleportCause cause,
        TeleportOptions options,
        String source,
        Instant createdAt) {
    public TeleportContext {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(createdAt, "createdAt");
        from = from.clone();
        target = target.clone();
        if (source == null || source.isBlank()) {
            source = "unknown";
        }
    }

    public TeleportContext withTarget(Location newTarget) {
        return new TeleportContext(playerId, from, newTarget, cause, options, source, createdAt);
    }
}

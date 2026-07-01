package com.cotani.teleport.api;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public record TeleportContext(
        Player player,
        Location from,
        Location target,
        TeleportCause cause,
        TeleportOptions options,
        String source,
        java.time.Instant createdAt) {
    public TeleportContext {
        Objects.requireNonNull(player, "player");
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
        return new TeleportContext(player, from, newTarget, cause, options, source, createdAt);
    }
}

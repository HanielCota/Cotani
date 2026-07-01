package com.cotani.teleport.api;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface PendingTeleportService {
    UUID schedule(
            Player player,
            Location target,
            Duration delay,
            TeleportOptions options,
            TeleportCause cause,
            String source);

    boolean cancel(Player player, TeleportCancelReason reason);

    boolean hasPending(Player player);

    Optional<PendingTeleportView> find(Player player);
}

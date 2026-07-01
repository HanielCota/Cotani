package com.cotani.teleport.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public sealed interface TeleportResult permits TeleportResult.Success, TeleportResult.Failure {
    Player player();

    Location from();

    Location to();

    record Success(Player player, Location from, Location to, long durationMillis) implements TeleportResult {}

    record Failure(
            Player player,
            Location from,
            Location to,
            TeleportFailureReason reason,
            @Nullable Throwable cause) implements TeleportResult {}
}

package com.cotani.teleport.api;

import java.util.UUID;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

public sealed interface TeleportResult permits TeleportResult.Success, TeleportResult.Failure {
    UUID playerId();

    Location from();

    Location to();

    record Success(UUID playerId, Location from, Location to, long durationMillis) implements TeleportResult {}

    record Failure(
            UUID playerId,
            Location from,
            Location to,
            TeleportFailureReason reason,
            @Nullable Throwable cause) implements TeleportResult {}
}

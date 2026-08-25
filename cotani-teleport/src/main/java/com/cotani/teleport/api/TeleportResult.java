package com.cotani.teleport.api;

import java.util.UUID;
import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

public sealed interface TeleportResult permits TeleportResult.Success, TeleportResult.Failure {
    UUID playerId();

    Location from();

    Location to();

    record Success(UUID playerId, Location from, Location to, long durationMillis) implements TeleportResult {
        public Success {
            java.util.Objects.requireNonNull(playerId, "playerId");
            java.util.Objects.requireNonNull(from, "from");
            java.util.Objects.requireNonNull(to, "to");
        }

        @Override
        public Location from() {
            return from.clone();
        }

        @Override
        public Location to() {
            return to.clone();
        }
    }

    record Failure(
            UUID playerId,
            Location from,
            Location to,
            TeleportFailureReason reason,
            @Nullable Throwable cause) implements TeleportResult {
        public Failure {
            java.util.Objects.requireNonNull(playerId, "playerId");
            java.util.Objects.requireNonNull(from, "from");
            java.util.Objects.requireNonNull(to, "to");
            java.util.Objects.requireNonNull(reason, "reason");
        }

        @Override
        public Location from() {
            return from.clone();
        }

        @Override
        public Location to() {
            return to.clone();
        }
    }
}

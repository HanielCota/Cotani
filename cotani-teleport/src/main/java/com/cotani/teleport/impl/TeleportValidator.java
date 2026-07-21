package com.cotani.teleport.impl;

import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportResults;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

public final class TeleportValidator {
    private TeleportValidator() {}

    public static Optional<TeleportResult.Failure> validateInitial(TeleportContext context, @Nullable Player player) {
        if (player == null || !player.isOnline()) {
            return Optional.of(TeleportResults.failure(context, TeleportFailureReason.PLAYER_OFFLINE));
        }
        if (context.target().getWorld() == null) {
            return Optional.of(TeleportResults.failure(context, TeleportFailureReason.WORLD_UNLOADED));
        }
        if (!isFinite(context.target())) {
            return Optional.of(TeleportResults.failure(context, TeleportFailureReason.INVALID_LOCATION));
        }
        return Optional.empty();
    }

    private static boolean isFinite(Location location) {
        return Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ());
    }
}

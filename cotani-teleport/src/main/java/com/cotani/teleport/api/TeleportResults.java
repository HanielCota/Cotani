package com.cotani.teleport.api;

import org.bukkit.Location;
import org.jspecify.annotations.Nullable;

public final class TeleportResults {
    private TeleportResults() {}

    public static TeleportResult.Success success(
            TeleportContext context, Location resolvedTarget, long durationMillis) {
        return new TeleportResult.Success(
                context.playerId(), context.from().clone(), resolvedTarget.clone(), durationMillis);
    }

    public static TeleportResult.Failure failure(TeleportContext context, TeleportFailureReason reason) {
        return failure(context, reason, null);
    }

    public static TeleportResult.Failure failure(
            TeleportContext context, TeleportFailureReason reason, @Nullable Throwable cause) {
        return new TeleportResult.Failure(
                context.playerId(), context.from().clone(), context.target().clone(), reason, cause);
    }
}

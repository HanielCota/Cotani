package com.cotani.teleport.api;

import org.jspecify.annotations.Nullable;

public final class TeleportResults {
    private TeleportResults() {}

    public static TeleportResult.Success success(
            TeleportContext context, org.bukkit.Location resolvedTarget, long durationMillis) {
        return new TeleportResult.Success(
                context.player(), context.from().clone(), resolvedTarget.clone(), durationMillis);
    }

    public static TeleportResult.Failure failure(TeleportContext context, TeleportFailureReason reason) {
        return failure(context, reason, null);
    }

    public static TeleportResult.Failure failure(
            TeleportContext context, TeleportFailureReason reason, @Nullable Throwable cause) {
        return new TeleportResult.Failure(
                context.player(), context.from().clone(), context.target().clone(), reason, cause);
    }
}

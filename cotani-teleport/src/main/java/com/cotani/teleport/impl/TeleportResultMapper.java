package com.cotani.teleport.impl;

import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportResults;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.bukkit.Location;

public final class TeleportResultMapper {
    private final TeleportEventNotifier eventNotifier;

    public TeleportResultMapper(TeleportEventNotifier eventNotifier) {
        this.eventNotifier = eventNotifier;
    }

    public CompletableFuture<TeleportResult> mapSuccess(
            TeleportContext context, Location from, Location eventTarget, Instant startedAt) {
        TeleportResult.Success result =
                TeleportResults.success(context, eventTarget, eventNotifier.elapsedMillis(startedAt));
        return eventNotifier
                .firePostTeleport(context.player(), from, eventTarget, result)
                .thenApply(_ -> result);
    }

    public CompletableFuture<TeleportResult> mapTeleportFailure(TeleportContext context) {
        TeleportResult.Failure failure = TeleportResults.failure(context, TeleportFailureReason.TELEPORT_FAILED);
        return eventNotifier.fireFailure(failure).thenApply(_ -> failure);
    }

    public CompletableFuture<TeleportResult> mapException(TeleportContext context, Throwable error) {
        TeleportFailureReason reason =
                switch (error) {
                    case TimeoutException _ -> TeleportFailureReason.TIMEOUT;
                    default -> TeleportFailureReason.UNKNOWN_ERROR;
                };
        TeleportResult.Failure failure = TeleportResults.failure(context, reason, error);
        return eventNotifier.fireFailure(failure).thenApply(_ -> failure);
    }
}

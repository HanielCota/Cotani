package com.cotani.teleport.impl;

import com.cotani.teleport.api.TeleportContext;
import com.cotani.teleport.api.TeleportFailureReason;
import com.cotani.teleport.api.TeleportResult;
import com.cotani.teleport.api.TeleportResults;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.bukkit.Location;

public final class TeleportResultMapper {
    private final TeleportEventNotifier eventNotifier;

    public TeleportResultMapper(TeleportEventNotifier eventNotifier) {
        this.eventNotifier = Objects.requireNonNull(eventNotifier, "eventNotifier");
    }

    public CompletionStage<TeleportResult> mapSuccess(
            TeleportContext context, Location from, Location eventTarget, Instant startedAt) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(eventTarget, "eventTarget");
        Objects.requireNonNull(startedAt, "startedAt");
        TeleportResult.Success result =
                TeleportResults.success(context, eventTarget, eventNotifier.elapsedMillis(startedAt));
        return eventNotifier
                .firePostTeleport(context.playerId(), from, eventTarget, result)
                .thenApply(_ -> result);
    }

    public CompletionStage<TeleportResult> mapTeleportFailure(TeleportContext context) {
        Objects.requireNonNull(context, "context");
        TeleportResult.Failure failure = TeleportResults.failure(context, TeleportFailureReason.TELEPORT_FAILED);
        return eventNotifier.fireFailure(failure).thenApply(_ -> failure);
    }

    public CompletionStage<TeleportResult> mapException(TeleportContext context, Throwable error) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(error, "error");
        Throwable cause = error;
        while (cause instanceof CompletionException || cause instanceof ExecutionException) {
            var nested = cause.getCause();
            if (nested == null) {
                break;
            }
            cause = nested;
        }
        TeleportFailureReason reason =
                cause instanceof TimeoutException ? TeleportFailureReason.TIMEOUT : TeleportFailureReason.UNKNOWN_ERROR;
        TeleportResult.Failure failure = TeleportResults.failure(context, reason, cause);
        return eventNotifier.fireFailure(failure).thenApply(_ -> failure);
    }
}

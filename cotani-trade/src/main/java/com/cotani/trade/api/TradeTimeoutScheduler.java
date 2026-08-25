package com.cotani.trade.api;

import com.cotani.AsyncCloseable;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/** Owns bounded timeout scheduling for trade operations. */
public interface TradeTimeoutScheduler extends AsyncCloseable {
    /**
     * Completes with the source result or exceptionally after {@code timeout}.
     *
     * <p>A timeout does not claim that the source operation was cancelled. Callers must preserve
     * idempotency and reconcile externally committed operations.
     */
    <T> CompletionStage<T> withTimeout(CompletionStage<T> stage, Duration timeout, String operationName);
}

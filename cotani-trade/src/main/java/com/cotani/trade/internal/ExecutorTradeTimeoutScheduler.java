package com.cotani.trade.internal;

import com.cotani.api.InternalApi;
import com.cotani.trade.api.TradeTimeoutScheduler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** JDK-backed timeout owner used by the trade factory. */
@InternalApi
public final class ExecutorTradeTimeoutScheduler implements TradeTimeoutScheduler {
    private final ScheduledExecutorService executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> closeStage = new CompletableFuture<>();

    public ExecutorTradeTimeoutScheduler() {
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "cotani-trade-timeouts");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public <T> CompletionStage<T> withTimeout(CompletionStage<T> stage, Duration timeout, String operationName) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(operationName, "operationName");
        if (closed.get()) {
            return CompletableFuture.failedFuture(new IllegalStateException("timeout scheduler is closed"));
        }

        var result = new CompletableFuture<T>();
        ScheduledFuture<?> timeoutTask;
        try {
            timeoutTask = executor.schedule(
                    () -> result.completeExceptionally(
                            new TimeoutException(operationName + " operation timed out after " + timeout)),
                    timeoutNanos(timeout),
                    TimeUnit.NANOSECONDS);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
        try {
            Objects.requireNonNull(
                    stage.whenComplete((value, failure) -> {
                        timeoutTask.cancel(false);
                        if (failure != null) {
                            result.completeExceptionally(failure);
                            return;
                        }
                        result.complete(value);
                    }),
                    "completion stage");
        } catch (RuntimeException failure) {
            timeoutTask.cancel(false);
            result.completeExceptionally(failure);
        }
        return result;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        if (!closed.compareAndSet(false, true)) {
            return closeStage;
        }
        executor.shutdown();
        closeStage.complete(null);
        return closeStage;
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return Math.max(1, timeout.toNanos());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }
}

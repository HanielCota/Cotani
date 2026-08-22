package com.cotani.economy.internal.event;

import com.cotani.api.InternalApi;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.task.api.AsyncTaskExecutor;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.util.VoidResult;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Publishes economy events on the server main thread.
 */
@InternalApi
public final class MainThreadEconomyEventPublisher implements EconomyEventPublisher {
    private final AsyncTaskExecutor scheduler;
    private final EconomyEventPublisher delegate;
    private final Logger logger;

    public MainThreadEconomyEventPublisher(
            PaperTaskScheduler scheduler, EconomyEventPublisher delegate, Logger logger) {
        this((AsyncTaskExecutor) scheduler, delegate, logger);
    }

    public MainThreadEconomyEventPublisher(AsyncTaskExecutor scheduler, EconomyEventPublisher delegate, Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void publish(EconomyTransactionEvent event) {
        var _ = publishAsync(event);
    }

    @Override
    public CompletionStage<Void> publishAsync(EconomyTransactionEvent event) {
        Objects.requireNonNull(event, "event");

        return scheduler
                .supply(ExecutionTarget.global(), "economy-event", () -> {
                    delegate.publish(event);
                    return VoidResult.nullValue();
                })
                .whenComplete((_, error) -> {
                    if (error != null) {
                        logger.log(Level.SEVERE, "Failed to publish economy transaction event", error);
                    }
                });
    }
}

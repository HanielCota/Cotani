package com.cotani.economy.internal.event;

import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.task.api.ExecutionTarget;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;

/**
 * Publishes economy events on the server main thread.
 */
public final class MainThreadEconomyEventPublisher implements EconomyEventPublisher {

    private final PaperTaskScheduler scheduler;
    private final EconomyEventPublisher delegate;

    public MainThreadEconomyEventPublisher(PaperTaskScheduler scheduler, EconomyEventPublisher delegate) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void publish(EconomyTransactionEvent event) {
        Objects.requireNonNull(event, "event");

        var _ = scheduler.supply(ExecutionTarget.global(), "economy-event", () -> {
            delegate.publish(event);
            return null;
        });
    }
}

package com.cotani.economy.internal.event;

import com.cotani.api.InternalApi;
import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import java.util.Objects;

@InternalApi
public final class NoopEconomyEventPublisher implements EconomyEventPublisher {

    @Override
    public void publish(EconomyTransactionEvent event) {
        Objects.requireNonNull(event, "event");
    }
}

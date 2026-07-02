package com.cotani.economy.event;

public interface EconomyEventPublisher {

    void publish(EconomyTransactionEvent event);
}

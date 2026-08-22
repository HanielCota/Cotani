package com.cotani.economy.event;

import com.cotani.task.util.CompletionStages;
import java.util.concurrent.CompletionStage;

public interface EconomyEventPublisher {
    void publish(EconomyTransactionEvent event);

    default CompletionStage<Void> publishAsync(EconomyTransactionEvent event) {
        publish(event);
        return CompletionStages.completedVoid();
    }
}

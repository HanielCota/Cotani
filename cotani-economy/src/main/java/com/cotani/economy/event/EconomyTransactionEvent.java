package com.cotani.economy.event;

import com.cotani.economy.transaction.EconomyTransaction;
import java.util.Objects;

public record EconomyTransactionEvent(EconomyTransaction transaction) {

    public EconomyTransactionEvent {
        Objects.requireNonNull(transaction, "transaction");
    }
}

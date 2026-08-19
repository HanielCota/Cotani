package com.cotani.economy.internal.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NoopEconomyEventPublisherTest {

    @Test
    void shouldAcceptAnyEventWithoutSideEffects() {
        var publisher = new NoopEconomyEventPublisher();
        var transaction = EconomyTransaction.deposit(
                EconomyOperationId.random(),
                UUID.randomUUID(),
                CurrencyId.of("coins"),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                EconomyReason.system("test"),
                Instant.now());

        assertDoesNotThrow(() -> publisher.publish(new EconomyTransactionEvent(transaction)));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullEvent() {
        var publisher = new NoopEconomyEventPublisher();

        assertThrows(NullPointerException.class, () -> publisher.publish(null));
    }
}

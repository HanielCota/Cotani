package com.cotani.economy.internal.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.cotani.economy.currency.CurrencyId;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.internal.event.BukkitEconomyEventPublisher.BukkitEconomyTransactionEvent;
import com.cotani.economy.transaction.EconomyOperationId;
import com.cotani.economy.transaction.EconomyReason;
import com.cotani.economy.transaction.EconomyTransaction;
import com.cotani.economy.transaction.EconomyTransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BukkitEconomyEventPublisherTest {

    @Test
    void shouldCallBukkitEventFromMainThread() {
        var pluginManager = mock(PluginManager.class);

        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            var transaction = sampleTransaction();

            BukkitEconomyEventPublisher.create().publish(new EconomyTransactionEvent(transaction));

            var captor = ArgumentCaptor.forClass(BukkitEconomyTransactionEvent.class);
            verify(pluginManager).callEvent(captor.capture());
            var dispatched = captor.getValue();
            assertSame(transaction, dispatched.transaction());
            assertEquals(EconomyTransactionType.DEPOSIT, dispatched.type());
        }
    }

    @Test
    void shouldRejectPublicationFromOffMainThread() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

            var publisher = BukkitEconomyEventPublisher.create();

            assertThrows(
                    IllegalStateException.class,
                    () -> publisher.publish(new EconomyTransactionEvent(sampleTransaction())));
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullEvent() {
        var publisher = BukkitEconomyEventPublisher.create();

        assertThrows(NullPointerException.class, () -> publisher.publish(null));
    }

    @Test
    void shouldExposeHandlerListAndTransactionAccessors() {
        var transaction = sampleTransaction();
        var event = new BukkitEconomyTransactionEvent(transaction);

        assertNotNull(event.getHandlers());
        assertSame(event.getHandlers(), BukkitEconomyTransactionEvent.getHandlerList());
        assertInstanceOf(HandlerList.class, event.getHandlers());
        assertSame(transaction, event.transaction());
        assertEquals(transaction.type(), event.type());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTransactionOnBukkitEvent() {
        assertThrows(NullPointerException.class, () -> new BukkitEconomyTransactionEvent(null));
    }

    private static EconomyTransaction sampleTransaction() {
        return EconomyTransaction.deposit(
                EconomyOperationId.random(),
                UUID.randomUUID(),
                CurrencyId.of("coins"),
                BigDecimal.TEN,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                EconomyReason.system("test"),
                Instant.now());
    }
}

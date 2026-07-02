package com.cotani.economy.internal.event;

import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.transaction.EconomyTransactionType;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Publishes economy events as Bukkit events.
 */
public final class BukkitEconomyEventPublisher implements EconomyEventPublisher {

    private BukkitEconomyEventPublisher() {}

    public static EconomyEventPublisher create() {
        return new BukkitEconomyEventPublisher();
    }

    @Override
    public void publish(EconomyTransactionEvent event) {
        Bukkit.getPluginManager().callEvent(new BukkitEconomyTransactionEvent(event.transaction()));
    }

    public static final class BukkitEconomyTransactionEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final com.cotani.economy.transaction.EconomyTransaction transaction;

        public BukkitEconomyTransactionEvent(com.cotani.economy.transaction.EconomyTransaction transaction) {
            this.transaction = transaction;
        }

        public com.cotani.economy.transaction.EconomyTransaction transaction() {
            return transaction;
        }

        public EconomyTransactionType type() {
            return transaction.type();
        }

        @Override
        public HandlerList getHandlers() {
            return HANDLERS;
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
        }
    }
}

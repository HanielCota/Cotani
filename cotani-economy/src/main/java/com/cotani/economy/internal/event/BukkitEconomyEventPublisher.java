package com.cotani.economy.internal.event;

import com.cotani.economy.event.EconomyEventPublisher;
import com.cotani.economy.event.EconomyTransactionEvent;
import com.cotani.economy.transaction.EconomyTransactionType;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Publishes economy events as Bukkit events.
 *
 * <p><b>Contract:</b> {@link #publish(EconomyTransactionEvent)} must be invoked from the server main
 * thread. Bukkit event dispatch ({@code PluginManager#callEvent}) is not thread-safe, so calling this
 * method from any other thread throws {@link IllegalStateException}.
 *
 * @apiNote Always publish from the main thread. If you are on an async thread, hand the event back to the
 *     main thread (for example via {@code PaperTaskScheduler#global}) before calling {@code publish}.
 */
public final class BukkitEconomyEventPublisher implements EconomyEventPublisher {

    private BukkitEconomyEventPublisher() {}

    public static EconomyEventPublisher create() {
        return new BukkitEconomyEventPublisher();
    }

    @Override
    public void publish(EconomyTransactionEvent event) {
        Objects.requireNonNull(event, "event");
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "BukkitEconomyEventPublisher.publish must be called from the server main thread");
        }
        Bukkit.getPluginManager().callEvent(new BukkitEconomyTransactionEvent(event.transaction()));
    }

    public static final class BukkitEconomyTransactionEvent extends Event {
        private static final HandlerList HANDLERS = new HandlerList();
        private final com.cotani.economy.transaction.EconomyTransaction transaction;

        public BukkitEconomyTransactionEvent(com.cotani.economy.transaction.EconomyTransaction transaction) {
            this.transaction = Objects.requireNonNull(transaction, "transaction");
        }

        public static HandlerList getHandlerList() {
            return HANDLERS;
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
    }
}

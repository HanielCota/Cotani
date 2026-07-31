package com.cotani.gui.context;

import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;

/**
 * Immutable snapshot of a click inside a Cotani GUI.
 *
 * <p>Instances are created on the thread that fired the Bukkit event (main thread on Paper, the
 * viewer's region thread on Folia) and must not escape into async flows.
 *
 * @param player the clicking player
 * @param clickType the Bukkit click type
 * @param slot the clicked slot inside the top inventory
 * @param view the open inventory view
 */
public record ClickContext(Player player, ClickType clickType, int slot, InventoryView view) {
    public ClickContext {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        Objects.requireNonNull(clickType, "Parameter 'clickType' must not be null");
        Objects.requireNonNull(view, "Parameter 'view' must not be null");
    }
}

package com.cotani.gui.button;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Dynamic item supplier evaluated on every render, allowing per-viewer content such as player heads.
 */
@FunctionalInterface
public interface ItemProvider {

    /**
     * Builds the item shown to the given viewer.
     *
     * @param viewer the player looking at the GUI
     * @return the item to display, never {@code null}
     */
    ItemStack provide(Player viewer);
}

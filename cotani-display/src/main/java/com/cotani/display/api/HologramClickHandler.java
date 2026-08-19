package com.cotani.display.api;

import org.bukkit.entity.Player;

/**
 * Functional callback invoked when a player interacts with a clickable hologram.
 */
@FunctionalInterface
public interface HologramClickHandler {

    /**
     * Handles an interaction with a hologram.
     *
     * @param player the clicking player
     * @param hologram the clicked hologram
     * @param clickType the type of click interaction
     */
    void handleClick(Player player, Hologram hologram, HologramClickType clickType);
}

package com.cotani.gui.context;

import java.util.Objects;
import org.bukkit.entity.Player;

/**
 * Immutable snapshot of a GUI close event.
 *
 * @param player the player whose GUI was closed
 */
public record CloseContext(Player player) {

    public CloseContext {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
    }
}

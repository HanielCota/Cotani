package com.cotani.gui.context;

import java.util.Map;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Immutable snapshot of a GUI close event.
 *
 * @param player the player whose GUI was closed
 * @param leftoverItems items still in interactable slots when the close handler started, keyed by slot
 */
public record CloseContext(Player player, Map<Integer, ItemStack> leftoverItems) {
    public CloseContext {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");
        leftoverItems = Map.copyOf(Objects.requireNonNull(leftoverItems, "Parameter 'leftoverItems' must not be null"));
    }

    /**
     * Creates a close snapshot with no leftover interactable items.
     *
     * @param player the player whose GUI was closed
     */
    public CloseContext(Player player) {
        this(player, Map.of());
    }
}

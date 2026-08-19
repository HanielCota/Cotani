package com.cotani.display.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Represents an active or unspawned multi-line Display Entity Hologram.
 */
public interface Hologram {

    /**
     * The unique identifier of this hologram.
     *
     * @return the UUID
     */
    UUID id();

    /**
     * The optional custom name/identifier of this hologram.
     *
     * @return the optional name
     */
    Optional<String> name();

    /**
     * The base location of this hologram.
     *
     * @return the base location
     */
    Location location();

    /**
     * An immutable snapshot of the lines in this hologram, ordered top to bottom.
     *
     * @return the list of hologram lines
     */
    List<HologramLine> lines();

    /**
     * Returns the number of lines currently configured.
     *
     * @return the line count
     */
    default int lineCount() {
        return lines().size();
    }

    /**
     * Checks if this hologram is currently spawned in the world.
     *
     * @return true if spawned
     */
    boolean isSpawned();

    /**
     * An immutable list of Bukkit entity UUIDs associated with this hologram.
     *
     * @return the entity UUIDs
     */
    List<UUID> entityIds();

    /**
     * Spawns this hologram at the specified location safely on the region thread.
     *
     * @param location the spawn location
     * @return a completion stage with this hologram instance
     */
    CompletionStage<Hologram> spawnAsync(Location location);

    /**
     * Teleports this hologram to a new location.
     *
     * @param location the new location
     * @return a completion stage for when teleport is finished
     */
    CompletionStage<Void> teleportAsync(Location location);

    /**
     * Updates an existing line at the specified index.
     *
     * @param index the 0-based line index
     * @param line the new line
     * @return a completion stage
     */
    CompletionStage<Void> updateLineAsync(int index, HologramLine line);

    /**
     * Updates an existing text line with a new Component.
     *
     * @param index the 0-based line index
     * @param component the new text
     * @return a completion stage
     */
    default CompletionStage<Void> updateLineAsync(int index, Component component) {
        return updateLineAsync(index, TextLine.of(component));
    }

    /**
     * Updates an existing item line with a new ItemStack.
     *
     * @param index the 0-based line index
     * @param item the new item
     * @return a completion stage
     */
    default CompletionStage<Void> updateLineAsync(int index, ItemStack item) {
        return updateLineAsync(index, ItemLine.of(item));
    }

    /**
     * Appends a new line to the bottom of the hologram.
     *
     * @param line the line to add
     * @return a completion stage
     */
    CompletionStage<Void> addLineAsync(HologramLine line);

    /**
     * Appends a new text line to the bottom of the hologram.
     *
     * @param component the text component
     * @return a completion stage
     */
    default CompletionStage<Void> addLineAsync(Component component) {
        return addLineAsync(TextLine.of(component));
    }

    /**
     * Appends a new item line to the bottom of the hologram.
     *
     * @param item the item stack
     * @return a completion stage
     */
    default CompletionStage<Void> addLineAsync(ItemStack item) {
        return addLineAsync(ItemLine.of(item));
    }

    /**
     * Removes a line at the specified index.
     *
     * @param index the 0-based index
     * @return a completion stage
     */
    CompletionStage<Void> removeLineAsync(int index);

    /**
     * Clears all lines and destroys the spawned display entities.
     *
     * @return a completion stage
     */
    CompletionStage<Void> clearLinesAsync();

    /**
     * Despawns and destroys all entities belonging to this hologram.
     *
     * @return a completion stage
     */
    CompletionStage<Void> destroyAsync();

    /**
     * Returns the click handler callback if configured.
     *
     * @return the optional click handler
     */
    Optional<HologramClickHandler> clickHandler();

    /**
     * Updates the click handler callback for this hologram.
     *
     * @param handler the new handler, or null to remove
     */
    void setClickHandler(@Nullable HologramClickHandler handler);
}

package com.cotani.display.api;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * Fluent builder for configuring and spawning a {@link Hologram}.
 */
public interface HologramBuilder {

    /**
     * Sets a custom name identifier for the hologram.
     *
     * @param name the name
     * @return this builder
     */
    HologramBuilder name(String name);

    /**
     * Appends a custom hologram line.
     *
     * @param line the line
     * @return this builder
     */
    HologramBuilder addLine(HologramLine line);

    /**
     * Appends a text line using an Adventure Component.
     *
     * @param component the text component
     * @return this builder
     */
    default HologramBuilder addLine(Component component) {
        Objects.requireNonNull(component, "component cannot be null");
        return addLine(TextLine.of(component));
    }

    /**
     * Appends a text line parsed via MiniMessage.
     *
     * @param miniMessageText the MiniMessage string
     * @return this builder
     */
    HologramBuilder addLine(String miniMessageText);

    /**
     * Appends a floating item line.
     *
     * @param item the item stack
     * @return this builder
     */
    default HologramBuilder addItemLine(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        return addLine(ItemLine.of(item));
    }

    /**
     * Appends a floating item line with custom scale.
     *
     * @param item the item stack
     * @param scale the scaling factor
     * @return this builder
     */
    default HologramBuilder addItemLine(ItemStack item, float scale) {
        Objects.requireNonNull(item, "item cannot be null");
        return addLine(ItemLine.of(item, scale));
    }

    /**
     * Appends a block display line.
     *
     * @param blockData the block data
     * @return this builder
     */
    default HologramBuilder addBlockLine(BlockData blockData) {
        Objects.requireNonNull(blockData, "blockData cannot be null");
        return addLine(BlockLine.of(blockData));
    }

    /**
     * Sets the default billboard mode for all lines in this hologram.
     *
     * @param billboard the billboard mode
     * @return this builder
     */
    HologramBuilder billboard(DisplayBillboard billboard);

    /**
     * Sets the vertical spacing multiplier between lines in blocks.
     *
     * @param spacing the vertical spacing (default 0.28)
     * @return this builder
     */
    HologramBuilder lineSpacing(double spacing);

    /**
     * Sets whether this hologram spawns a clickable hitbox.
     *
     * @param clickable true to enable click interaction
     * @return this builder
     */
    HologramBuilder clickable(boolean clickable);

    /**
     * Configures a click interaction callback and enables the clickable hitbox.
     *
     * @param handler the click callback
     * @return this builder
     */
    HologramBuilder onClick(HologramClickHandler handler);

    /**
     * Builds the unspawned Hologram instance.
     *
     * @return the built Hologram
     */
    Hologram build();

    /**
     * Builds and spawns the hologram at the specified location safely on the region thread.
     *
     * @param location the spawn location
     * @return a completion stage with the spawned Hologram
     */
    CompletionStage<Hologram> spawnAsync(Location location);
}

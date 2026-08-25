package com.cotani.display.api;

import java.util.Objects;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.inventory.ItemStack;

/**
 * An immutable item display line rendered via an {@link org.bukkit.entity.ItemDisplay} entity.
 */
public record ItemLine(
        ItemStack item,
        ItemDisplayTransform itemTransform,
        DisplayBillboard billboard,
        float scale,
        float viewRange,
        double heightOffset,
        boolean spin)
        implements HologramLine {

    public static final double DEFAULT_ITEM_HEIGHT_OFFSET = 0.5;

    public ItemLine {
        Objects.requireNonNull(item, "item cannot be null");
        Objects.requireNonNull(itemTransform, "itemTransform cannot be null");
        Objects.requireNonNull(billboard, "billboard cannot be null");
        ItemStack cloned = null;
        try {
            cloned = item.clone();
        } catch (Exception exception) {
            java.util.logging.Logger.getLogger(ItemLine.class.getName())
                    .log(java.util.logging.Level.FINE, "Could not clone item display stack", exception);
        }
        item = cloned != null ? cloned : item;
    }

    /**
     * Creates a standard floating item line.
     *
     * @param item the item stack
     * @return the created item line
     */
    public static ItemLine of(ItemStack item) {
        Objects.requireNonNull(item, "item cannot be null");
        return new ItemLine(
                item,
                ItemDisplayTransform.FIXED,
                DisplayBillboard.CENTER,
                0.6f,
                1.0f,
                DEFAULT_ITEM_HEIGHT_OFFSET,
                false);
    }

    /**
     * Creates a spinning floating item line.
     *
     * @param item the item stack
     * @param scale the scaling factor
     * @return the created item line
     */
    public static ItemLine of(ItemStack item, float scale) {
        Objects.requireNonNull(item, "item cannot be null");
        return new ItemLine(
                item,
                ItemDisplayTransform.FIXED,
                DisplayBillboard.CENTER,
                scale,
                1.0f,
                DEFAULT_ITEM_HEIGHT_OFFSET,
                false);
    }

    /**
     * Returns a copy with the updated item stack.
     *
     * @param newItem the new item stack
     * @return the updated ItemLine
     */
    public ItemLine withItem(ItemStack newItem) {
        Objects.requireNonNull(newItem, "newItem cannot be null");
        return new ItemLine(newItem, itemTransform, billboard, scale, viewRange, heightOffset, spin);
    }

    /**
     * Returns a copy with the specified billboard mode.
     *
     * @param newBillboard the billboard mode
     * @return the updated ItemLine
     */
    public ItemLine withBillboard(DisplayBillboard newBillboard) {
        Objects.requireNonNull(newBillboard, "newBillboard cannot be null");
        return new ItemLine(item, itemTransform, newBillboard, scale, viewRange, heightOffset, spin);
    }

    /**
     * Returns a copy with the specified scale.
     *
     * @param newScale the scale factor
     * @return the updated ItemLine
     */
    public ItemLine withScale(float newScale) {
        return new ItemLine(item, itemTransform, billboard, newScale, viewRange, heightOffset, spin);
    }

    /**
     * Returns a copy with the specified height offset.
     *
     * @param offset the vertical offset in blocks
     * @return the updated ItemLine
     */
    public ItemLine withHeightOffset(double offset) {
        return new ItemLine(item, itemTransform, billboard, scale, viewRange, offset, spin);
    }
}

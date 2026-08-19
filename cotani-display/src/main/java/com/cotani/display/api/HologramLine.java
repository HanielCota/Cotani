package com.cotani.display.api;

/**
 * Common sealed contract representing an individual line or layer inside a {@link Hologram}.
 */
public sealed interface HologramLine permits TextLine, ItemLine, BlockLine {

    /**
     * The billboard orientation for this line.
     *
     * @return the billboard mode
     */
    DisplayBillboard billboard();

    /**
     * The uniform scaling multiplier for this line.
     *
     * @return the scale factor
     */
    float scale();

    /**
     * The maximum view distance range ratio for this line.
     *
     * @return the view range multiplier
     */
    float viewRange();

    /**
     * The vertical height offset of this line in blocks.
     *
     * @return the height offset
     */
    double heightOffset();
}

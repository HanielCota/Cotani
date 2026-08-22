package com.cotani.display.api;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.jspecify.annotations.Nullable;

/**
 * An immutable text line rendered via a {@link org.bukkit.entity.TextDisplay} entity.
 */
public record TextLine(
        Component text,
        DisplayBillboard billboard,
        @Nullable Color backgroundColor,
        boolean shadow,
        boolean seeThrough,
        byte textOpacity,
        float scale,
        float viewRange,
        double heightOffset)
        implements HologramLine {

    public static final double DEFAULT_HEIGHT_OFFSET = 0.28;

    public TextLine {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(billboard, "billboard cannot be null");
    }

    /**
     * Creates a standard text line with default display properties.
     *
     * @param text the Adventure component text
     * @return the created text line
     */
    public static TextLine of(Component text) {
        Objects.requireNonNull(text, "text cannot be null");
        return new TextLine(
                text, DisplayBillboard.CENTER, null, true, false, (byte) -1, 1.0f, 1.0f, DEFAULT_HEIGHT_OFFSET);
    }

    /**
     * Creates a text line parsed from a MiniMessage string.
     *
     * @param miniMessageText the MiniMessage formatted string
     * @return the created text line
     */
    public static TextLine of(String miniMessageText) {
        Objects.requireNonNull(miniMessageText, "miniMessageText cannot be null");
        return of(com.cotani.text.MiniMessages.parse(miniMessageText));
    }

    /**
     * Creates a text line with specified billboard and scale.
     *
     * @param text the Adventure component text
     * @param billboard the billboard mode
     * @param scale the scaling factor
     * @return the created text line
     */
    public static TextLine of(Component text, DisplayBillboard billboard, float scale) {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(billboard, "billboard cannot be null");
        return new TextLine(text, billboard, null, true, false, (byte) -1, scale, 1.0f, DEFAULT_HEIGHT_OFFSET);
    }

    /**
     * Returns a copy of this text line with the updated text component.
     *
     * @param newText the new component
     * @return the updated TextLine
     */
    public TextLine withText(Component newText) {
        Objects.requireNonNull(newText, "newText cannot be null");
        return new TextLine(
                newText, billboard, backgroundColor, shadow, seeThrough, textOpacity, scale, viewRange, heightOffset);
    }

    /**
     * Returns a copy with the specified background color.
     *
     * @param color the background color (null for default translucent dark)
     * @return the updated TextLine
     */
    public TextLine withBackground(@Nullable Color color) {
        return new TextLine(text, billboard, color, shadow, seeThrough, textOpacity, scale, viewRange, heightOffset);
    }

    /**
     * Returns a copy with the specified billboard mode.
     *
     * @param newBillboard the billboard mode
     * @return the updated TextLine
     */
    public TextLine withBillboard(DisplayBillboard newBillboard) {
        Objects.requireNonNull(newBillboard, "newBillboard cannot be null");
        return new TextLine(
                text, newBillboard, backgroundColor, shadow, seeThrough, textOpacity, scale, viewRange, heightOffset);
    }

    /**
     * Returns a copy with the specified scale.
     *
     * @param newScale the scale factor
     * @return the updated TextLine
     */
    public TextLine withScale(float newScale) {
        return new TextLine(
                text, billboard, backgroundColor, shadow, seeThrough, textOpacity, newScale, viewRange, heightOffset);
    }

    /**
     * Returns a copy with the specified text opacity.
     *
     * @param opacity the text opacity byte
     * @return the updated TextLine
     */
    public TextLine withOpacity(byte opacity) {
        return new TextLine(
                text, billboard, backgroundColor, shadow, seeThrough, opacity, scale, viewRange, heightOffset);
    }

    /**
     * Returns a copy with the specified height offset.
     *
     * @param offset the vertical offset in blocks
     * @return the updated TextLine
     */
    public TextLine withHeightOffset(double offset) {
        return new TextLine(
                text, billboard, backgroundColor, shadow, seeThrough, textOpacity, scale, viewRange, offset);
    }
}

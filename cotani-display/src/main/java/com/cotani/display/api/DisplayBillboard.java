package com.cotani.display.api;

import org.bukkit.entity.Display.Billboard;
import org.jspecify.annotations.Nullable;

/**
 * Defines the billboard orientation of a display entity.
 */
public enum DisplayBillboard {
    /**
     * Center orientation: the entity always faces the viewer.
     */
    CENTER(Billboard.CENTER),

    /**
     * Fixed orientation: the entity maintains its spawn rotation.
     */
    FIXED(Billboard.FIXED),

    /**
     * Vertical orientation: the entity rotates around its vertical axis to face the viewer.
     */
    VERTICAL(Billboard.VERTICAL),

    /**
     * Horizontal orientation: the entity rotates around its horizontal axis to face the viewer.
     */
    HORIZONTAL(Billboard.HORIZONTAL);

    private final Billboard bukkitBillboard;

    DisplayBillboard(Billboard bukkitBillboard) {
        this.bukkitBillboard = bukkitBillboard;
    }

    /**
     * Returns the corresponding Bukkit {@link Billboard} representation.
     *
     * @return the Bukkit billboard
     */
    public Billboard toBukkit() {
        return bukkitBillboard;
    }

    /**
     * Converts a Bukkit {@link Billboard} to a {@link DisplayBillboard}.
     *
     * @param billboard the Bukkit billboard
     * @return the corresponding DisplayBillboard
     */
    public static DisplayBillboard fromBukkit(@Nullable Billboard billboard) {
        if (billboard == null) {
            return CENTER;
        }
        return switch (billboard) {
            case FIXED -> FIXED;
            case VERTICAL -> VERTICAL;
            case HORIZONTAL -> HORIZONTAL;
            case CENTER -> CENTER;
        };
    }
}

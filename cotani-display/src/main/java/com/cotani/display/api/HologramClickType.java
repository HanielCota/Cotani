package com.cotani.display.api;

/**
 * Defines the type of interaction performed on a hologram.
 */
public enum HologramClickType {
    LEFT_CLICK,
    RIGHT_CLICK,
    SHIFT_LEFT_CLICK,
    SHIFT_RIGHT_CLICK;

    /**
     * Determines whether this click includes the shift modifier.
     *
     * @return true if shift was held during click
     */
    public boolean isShiftClick() {
        return this == SHIFT_LEFT_CLICK || this == SHIFT_RIGHT_CLICK;
    }

    /**
     * Determines whether this is a left click.
     *
     * @return true if left click
     */
    public boolean isLeftClick() {
        return this == LEFT_CLICK || this == SHIFT_LEFT_CLICK;
    }

    /**
     * Determines whether this is a right click.
     *
     * @return true if right click
     */
    public boolean isRightClick() {
        return this == RIGHT_CLICK || this == SHIFT_RIGHT_CLICK;
    }
}

package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cotani.display.api.HologramClickType;
import org.junit.jupiter.api.Test;

class HologramClickTypeTest {

    @Test
    void shouldIdentifyLeftAndRightClicks() {
        assertTrue(HologramClickType.LEFT_CLICK.isLeftClick());
        assertFalse(HologramClickType.LEFT_CLICK.isRightClick());
        assertFalse(HologramClickType.LEFT_CLICK.isShiftClick());

        assertTrue(HologramClickType.RIGHT_CLICK.isRightClick());
        assertFalse(HologramClickType.RIGHT_CLICK.isLeftClick());
        assertFalse(HologramClickType.RIGHT_CLICK.isShiftClick());

        assertTrue(HologramClickType.SHIFT_LEFT_CLICK.isLeftClick());
        assertTrue(HologramClickType.SHIFT_LEFT_CLICK.isShiftClick());

        assertTrue(HologramClickType.SHIFT_RIGHT_CLICK.isRightClick());
        assertTrue(HologramClickType.SHIFT_RIGHT_CLICK.isShiftClick());
    }
}

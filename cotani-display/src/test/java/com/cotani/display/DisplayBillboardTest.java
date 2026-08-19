package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cotani.display.api.DisplayBillboard;
import org.bukkit.entity.Display.Billboard;
import org.junit.jupiter.api.Test;

class DisplayBillboardTest {

    @Test
    void shouldMapAllBillboardsCorrectly() {
        assertEquals(Billboard.CENTER, DisplayBillboard.CENTER.toBukkit());
        assertEquals(Billboard.FIXED, DisplayBillboard.FIXED.toBukkit());
        assertEquals(Billboard.VERTICAL, DisplayBillboard.VERTICAL.toBukkit());
        assertEquals(Billboard.HORIZONTAL, DisplayBillboard.HORIZONTAL.toBukkit());

        assertEquals(DisplayBillboard.CENTER, DisplayBillboard.fromBukkit(Billboard.CENTER));
        assertEquals(DisplayBillboard.FIXED, DisplayBillboard.fromBukkit(Billboard.FIXED));
        assertEquals(DisplayBillboard.VERTICAL, DisplayBillboard.fromBukkit(Billboard.VERTICAL));
        assertEquals(DisplayBillboard.HORIZONTAL, DisplayBillboard.fromBukkit(Billboard.HORIZONTAL));
        assertEquals(DisplayBillboard.CENTER, DisplayBillboard.fromBukkit(null));
    }
}

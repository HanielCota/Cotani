package com.cotani.display;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cotani.display.api.BlockLine;
import com.cotani.display.api.DisplayBillboard;
import com.cotani.display.api.ItemLine;
import com.cotani.display.api.TextLine;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class HologramLineTest {

    @Test
    void shouldCreateAndModifyTextLine() {
        var component = Component.text("Hello World");
        var line = TextLine.of(component);

        assertEquals(component, line.text());
        assertEquals(DisplayBillboard.CENTER, line.billboard());
        assertTrue(line.shadow());
        assertNull(line.backgroundColor());
        assertEquals(TextLine.DEFAULT_HEIGHT_OFFSET, line.heightOffset());

        var updated = line.withText(Component.text("Updated"))
                .withBackground(Color.BLACK)
                .withHeightOffset(0.5);

        assertEquals(Component.text("Updated"), updated.text());
        assertEquals(Color.BLACK, updated.backgroundColor());
        assertEquals(0.5, updated.heightOffset());
    }

    @Test
    void shouldCreateAndModifyItemLine() {
        var item = mock(ItemStack.class);
        var line = ItemLine.of(item, 1.2f);

        assertEquals(item, line.item());
        assertEquals(1.2f, line.scale());
        assertEquals(ItemLine.DEFAULT_ITEM_HEIGHT_OFFSET, line.heightOffset());

        var newItem = mock(ItemStack.class);
        var updated = line.withItem(newItem).withHeightOffset(0.8);

        assertEquals(newItem, updated.item());
        assertEquals(0.8, updated.heightOffset());
    }

    @Test
    void shouldCreateAndModifyBlockLine() {
        var blockData = mock(BlockData.class);
        var line = BlockLine.of(blockData, DisplayBillboard.FIXED, 0.8f);

        assertEquals(blockData, line.blockData());
        assertEquals(DisplayBillboard.FIXED, line.billboard());
        assertEquals(0.8f, line.scale());
        assertEquals(BlockLine.DEFAULT_BLOCK_HEIGHT_OFFSET, line.heightOffset());

        var newBlockData = mock(BlockData.class);
        var updated = line.withBlockData(newBlockData).withHeightOffset(1.0);

        assertEquals(newBlockData, updated.blockData());
        assertEquals(1.0, updated.heightOffset());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldThrowOnNullArguments() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> TextLine.of((Component) null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> TextLine.of((String) null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> ItemLine.of(null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> BlockLine.of(null));

        var textLine = TextLine.of(Component.text("Hi"));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> textLine.withText(null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> textLine.withBillboard(null));

        var itemLine = ItemLine.of(mock(ItemStack.class));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> itemLine.withItem(null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> itemLine.withBillboard(null));

        var blockLine = BlockLine.of(mock(BlockData.class));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> blockLine.withBlockData(null));
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> blockLine.withBillboard(null));
    }
}

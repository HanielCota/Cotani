package com.cotani.gui.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.Test;

final class ClickContextTest {
    private final Player player = mock(Player.class);
    private final InventoryView view = mock(InventoryView.class);

    @Test
    void shouldExposeSnapshotValues() {
        var context = new ClickContext(player, ClickType.SHIFT_RIGHT, 7, view);

        assertSame(player, context.player());
        assertEquals(ClickType.SHIFT_RIGHT, context.clickType());
        assertEquals(7, context.slot());
        assertSame(view, context.view());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPlayer() {
        assertThrows(NullPointerException.class, () -> new ClickContext(null, ClickType.LEFT, 0, view));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullClickType() {
        assertThrows(NullPointerException.class, () -> new ClickContext(player, null, 0, view));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullView() {
        assertThrows(NullPointerException.class, () -> new ClickContext(player, ClickType.LEFT, 0, null));
    }

    @Test
    void shouldExposePlayerInCloseContext() {
        var context = new CloseContext(player);

        assertSame(player, context.player());
        assertTrue(context.leftoverItems().isEmpty());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPlayerInCloseContext() {
        assertThrows(NullPointerException.class, () -> new CloseContext(null));
    }
}

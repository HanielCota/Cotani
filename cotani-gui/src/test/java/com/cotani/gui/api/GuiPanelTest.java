package com.cotani.gui.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cotani.gui.button.Button;
import com.cotani.gui.context.ClickContext;
import com.cotani.gui.context.CloseContext;
import com.cotani.gui.state.State;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Tests the pure panel logic (slot dispatch, dispose pipeline, subscription lifecycle) using a
 * mocked {@link Inventory} returned by a mocked {@code Bukkit.createInventory} static call.
 */
final class GuiPanelTest {
    private static final int ROWS = 3;
    private static final int SLOT_COUNT = ROWS * 9;

    private final Player viewer = mock(Player.class);
    private final Inventory inventory = mock(Inventory.class);
    private final InventoryView view = mock(InventoryView.class);
    private final UUID viewerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(viewer.getUniqueId()).thenReturn(viewerId);
        when(viewer.getName()).thenReturn("Steve");
        when(inventory.getSize()).thenReturn(SLOT_COUNT);
    }

    private GuiPanel createPanel(java.util.function.Consumer<CloseContext> closeHandler) {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(inventory);
            return GuiPanel.create(viewer, Component.text("Titulo"), ROWS, null, closeHandler);
        }
    }

    private ClickContext context(int slot) {
        return new ClickContext(viewer, ClickType.LEFT, slot, view);
    }

    @Test
    void shouldExposeViewerId() {
        var panel = createPanel(_ -> {});

        assertEquals(viewerId, panel.viewerId());
    }

    @Test
    void shouldCreateInventoryWithRowsAndTitle() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            when(Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(inventory);

            var panel = GuiPanel.create(viewer, Component.text("Titulo"), ROWS, null, _ -> {});

            bukkit.verify(() ->
                    Bukkit.createInventory(any(InventoryHolder.class), eq(SLOT_COUNT), eq(Component.text("Titulo"))));
            assertSame(inventory, panel.getInventory());
        }
    }

    @Test
    void shouldDispatchClickToBoundButtonOnly() {
        List<ClickContext> received = new ArrayList<>();
        var panel = createPanel(_ -> {});
        panel.bindSlot(0, Button.of(_ -> mock(ItemStack.class), received::add));

        panel.handleClick(context(0));
        panel.handleClick(context(5));

        assertEquals(1, received.size());
        assertEquals(0, received.get(0).slot());
    }

    @Test
    void shouldIgnoreClicksAfterDispose() {
        List<ClickContext> received = new ArrayList<>();
        var panel = createPanel(_ -> {});
        panel.bindSlot(0, Button.of(_ -> mock(ItemStack.class), received::add));

        panel.handleClose();
        panel.handleClick(context(0));

        assertTrue(received.isEmpty());
    }

    @Test
    void shouldRunCloseHandlerExactlyOnce() {
        List<CloseContext> closed = new ArrayList<>();
        var panel = createPanel(closed::add);

        panel.handleClose();
        panel.handleClose();

        assertEquals(1, closed.size());
        assertSame(viewer, closed.get(0).player());
        assertTrue(panel.isDisposed());
    }

    @Test
    void shouldClosePropertySubscriptionsOnDispose() {
        ItemStack item = mock(ItemStack.class);
        var panel = createPanel(_ -> {});
        panel.bindSlot(0, Button.of(_ -> item, _ -> {}));
        var property = State.of("a");
        panel.observe(property, List.of(0));
        clearInvocations(inventory);

        property.set("b");
        verify(inventory).setItem(eq(0), eq(item));

        panel.handleClose();
        clearInvocations(inventory);

        property.set("c");

        verifyNoInteractions(inventory);
    }

    @Test
    void shouldSkipSetItemWhenRenderedItemIsUnchanged() {
        ItemStack item = mock(ItemStack.class);
        var panel = createPanel(_ -> {});
        panel.bindSlot(0, Button.of(_ -> item, _ -> {}));
        when(inventory.getItem(0)).thenReturn(item);

        panel.render();

        verify(inventory, never()).setItem(anyInt(), any());
    }

    @Test
    void shouldRenderBorderItemForUnboundSlots() {
        ItemStack borderItem = mock(ItemStack.class);
        ItemStack buttonItem = mock(ItemStack.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(inventory);

            var panel = GuiPanel.create(viewer, Component.text("Titulo"), ROWS, borderItem, _ -> {});
            panel.bindSlot(0, Button.of(_ -> buttonItem, _ -> {}));

            panel.render();

            verify(inventory).setItem(eq(0), eq(buttonItem));
            verify(inventory).setItem(eq(5), eq(borderItem));
        }
    }

    @Test
    void shouldReRenderOnlyObservedSlotsOnPropertyChange() {
        ItemStack itemA = mock(ItemStack.class);
        ItemStack itemB = mock(ItemStack.class);
        var panel = createPanel(_ -> {});
        panel.bindSlot(1, Button.of(_ -> itemA, _ -> {}));
        panel.bindSlot(2, Button.of(_ -> itemB, _ -> {}));
        var property = State.of(0);
        panel.observe(property, List.of(1, 2));
        clearInvocations(inventory);

        property.set(1);

        verify(inventory).setItem(eq(1), eq(itemA));
        verify(inventory).setItem(eq(2), eq(itemB));
        verify(inventory, never()).setItem(eq(0), any());
    }

    @Test
    void shouldCloseViewerInventoryOnClose() {
        var panel = createPanel(_ -> {});

        panel.close();

        verify(viewer).closeInventory();
        assertFalse(panel.isDisposed());
    }
}

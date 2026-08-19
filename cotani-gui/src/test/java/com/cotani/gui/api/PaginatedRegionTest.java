package com.cotani.gui.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cotani.gui.button.Button;
import com.cotani.gui.state.State;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Behavior tests for the package-private {@link PaginatedRegion}: page slicing, slot clearing and
 * page clamping, rendered into a real {@link GuiPanel} with a mocked inventory.
 */
final class PaginatedRegionTest {
    private final Player viewer = mock(Player.class);
    private final Inventory inventory = mock(Inventory.class);

    @BeforeEach
    void setUp() {
        when(inventory.getSize()).thenReturn(27);
    }

    private GuiPanel createPanel() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(inventory);
            return GuiPanel.create(viewer, Component.text("Titulo"), 3, null, _ -> {});
        }
    }

    private Map<String, ItemStack> itemsFor(String... labels) {
        var items = new HashMap<String, ItemStack>();
        for (var label : labels) {
            items.put(label, mock(ItemStack.class));
        }
        return items;
    }

    private static Function<String, Button> rendererFor(Map<String, ItemStack> items) {
        return label -> Button.item(Objects.requireNonNull(items.get(label)));
    }

    @Test
    void shouldFillSlotsWithRenderedItemsForCurrentPage() {
        var panel = createPanel();
        var items = itemsFor("a", "b", "c");
        var region = new PaginatedRegion<>(List.of(0, 1, 2), State.of(0), List.of("a", "b", "c"), rendererFor(items));

        region.renderInto(panel);

        verify(inventory).setItem(eq(0), eq(items.get("a")));
        verify(inventory).setItem(eq(1), eq(items.get("b")));
        verify(inventory).setItem(eq(2), eq(items.get("c")));
    }

    @Test
    void shouldSliceItemsPerPageAndLeaveRemainingSlotsEmpty() {
        var panel = createPanel();
        var items = itemsFor("a", "b", "c");
        var region = new PaginatedRegion<>(List.of(0, 1), State.of(1), List.of("a", "b", "c"), rendererFor(items));

        region.renderInto(panel);

        verify(inventory).setItem(eq(0), eq(items.get("c")));
        verify(inventory, never()).setItem(eq(1), any());
    }

    @Test
    void shouldClampOutOfRangePageToLastPage() {
        var panel = createPanel();
        var items = itemsFor("a", "b", "c");
        var page = State.of(99);
        var region = new PaginatedRegion<>(List.of(0, 1), page, List.of("a", "b", "c"), rendererFor(items));

        region.renderInto(panel);

        assertEquals(1, page.get());
        verify(inventory).setItem(eq(0), eq(items.get("c")));
    }

    @Test
    void shouldClampNegativePageToZero() {
        var panel = createPanel();
        var items = itemsFor("a", "b", "c");
        var page = State.of(-7);
        var region = new PaginatedRegion<>(List.of(0, 1), page, List.of("a", "b", "c"), rendererFor(items));

        region.renderInto(panel);

        assertEquals(0, page.get());
        verify(inventory).setItem(eq(0), eq(items.get("a")));
        verify(inventory).setItem(eq(1), eq(items.get("b")));
    }

    @Test
    void shouldIgnoreEmptySlotList() {
        var panel = createPanel();
        var items = itemsFor("a", "b", "c");
        var page = State.of(0);
        var region = new PaginatedRegion<>(List.of(), page, List.of("a", "b", "c"), rendererFor(items));

        region.renderInto(panel);

        verifyNoInteractions(inventory);
    }
}

package com.cotani.gui.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cotani.gui.button.Button;
import com.cotani.gui.state.State;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Builder contract tests for {@link GuiWindow}: argument validation plus the full open pipeline
 * (structure layout, slot binding, paginated regions and inventory presentation).
 */
final class GuiWindowTest {
    private static final String ROW_WITH_B = "B . . . . . . . .";
    private static final String ROW_WITH_X = "X . . . . . . . .";

    private final Player player = mock(Player.class);
    private final Inventory inventory = mock(Inventory.class);
    private final ItemStack buttonItem = mock(ItemStack.class);

    @BeforeEach
    void setUp() {
        when(inventory.getSize()).thenReturn(18);
    }

    private record Opened(GuiPanel panel, Property<Integer> page, Map<String, ItemStack> items) {}

    private Opened openPaginatedWindow() {
        var page = State.of(0);
        var items = new HashMap<String, ItemStack>();
        var buttons = new HashMap<String, Button>();

        for (var label : List.of("a", "b", "c")) {
            var item = mock(ItemStack.class);
            items.put(label, item);
            buttons.put(label, Button.item(item));
        }

        var window = GuiWindow.panel("Titulo")
                .structure(ROW_WITH_B, ROW_WITH_X)
                .bind('B', Button.item(buttonItem))
                .paginated('X', page, List.of("a", "b", "c"), label -> Objects.requireNonNull(buttons.get(label)))
                .onClose(_ -> {});

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(inventory);
            return new Opened(window.open(player), page, items);
        }
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullTitles() {
        assertThrows(NullPointerException.class, () -> GuiWindow.panel((String) null));
        assertThrows(NullPointerException.class, () -> GuiWindow.panel((Component) null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPlayerOnOpen() {
        assertThrows(NullPointerException.class, () -> GuiWindow.panel("Titulo").open(null));
    }

    @Test
    void shouldRequireStructureBeforeOpen() {
        assertThrows(NullPointerException.class, () -> GuiWindow.panel("Titulo").open(player));
    }

    @Test
    void shouldRejectOpenWhenBoundSymbolIsMissingFromStructure() {
        var window = GuiWindow.panel("Titulo").structure(ROW_WITH_B).bind('Z', Button.item(buttonItem));

        assertThrows(IllegalArgumentException.class, () -> window.open(player));
    }

    @Test
    void shouldRejectBindingTheEmptySymbol() {
        var window = GuiWindow.panel("Titulo");

        assertThrows(IllegalArgumentException.class, () -> window.bind('.', Button.item(buttonItem)));
        assertThrows(IllegalArgumentException.class, () -> window.bind('.', buttonItem));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullButtonOnBind() {
        var window = GuiWindow.panel("Titulo");

        assertThrows(NullPointerException.class, () -> window.bind('B', (Button) null));
        assertThrows(NullPointerException.class, () -> window.bind('B', (ItemStack) null));
    }

    @Test
    void shouldValidateRowsRange() {
        var window = GuiWindow.panel("Titulo");

        assertThrows(IllegalArgumentException.class, () -> window.rows(0));
        assertThrows(IllegalArgumentException.class, () -> window.rows(7));
    }

    @Test
    void shouldRejectRowsMismatchingStructure() {
        var window = GuiWindow.panel("Titulo").structure(ROW_WITH_B, ROW_WITH_B);

        assertThrows(IllegalArgumentException.class, () -> window.rows(1));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullBorderMaterial() {
        assertThrows(NullPointerException.class, () -> GuiWindow.panel("Titulo").border(null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullBindToggleArguments() {
        var window = GuiWindow.panel("Titulo");

        assertThrows(
                NullPointerException.class,
                () -> window.bindToggle('B', null, Material.STONE, "t", "on", "off", _ -> {}));
        assertThrows(
                NullPointerException.class,
                () -> window.bindToggle('B', State.of(true), null, "t", "on", "off", _ -> {}));
        assertThrows(
                NullPointerException.class,
                () -> window.bindToggle('B', State.of(true), Material.STONE, null, "on", "off", _ -> {}));
        assertThrows(
                NullPointerException.class,
                () -> window.bindToggle('B', State.of(true), Material.STONE, "t", null, "off", _ -> {}));
        assertThrows(
                NullPointerException.class,
                () -> window.bindToggle('B', State.of(true), Material.STONE, "t", "on", null, _ -> {}));
        assertThrows(
                NullPointerException.class,
                () -> window.bindToggle('B', State.of(true), Material.STONE, "t", "on", "off", null));
        assertThrows(NullPointerException.class, () -> window.bindToggle('B', null, Material.STONE, "t", _ -> {}));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPaginatedArguments() {
        var window = GuiWindow.panel("Titulo");

        assertThrows(
                NullPointerException.class,
                () -> window.paginated('X', null, List.of("a"), _ -> Button.item(buttonItem)));
        assertThrows(
                NullPointerException.class,
                () -> window.paginated('X', State.of(0), null, _ -> Button.item(buttonItem)));
        assertThrows(NullPointerException.class, () -> window.paginated('X', State.of(0), List.of("a"), null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullCloseHandler() {
        assertThrows(NullPointerException.class, () -> GuiWindow.panel("Titulo").onClose(null));
    }

    @Test
    void shouldOpenWindowBindingSlotsAndRenderingRegions() {
        var opened = openPaginatedWindow();

        verify(player).openInventory(inventory);
        verify(inventory).setItem(eq(0), eq(buttonItem));
        verify(inventory).setItem(eq(9), eq(opened.items().get("a")));
        assertSame(inventory, opened.panel().getInventory());
    }

    @Test
    void shouldReRenderPaginatedRegionWhenPageChanges() {
        var opened = openPaginatedWindow();
        clearInvocations(inventory);

        opened.page().set(1);

        verify(inventory).setItem(eq(9), eq(opened.items().get("b")));
    }

    @Test
    void shouldConfigureInteractableSlotsOnOpen() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)))
                    .thenReturn(inventory);

            var panel = GuiWindow.panel("Input Window")
                    .structure("I . .")
                    .allowPlayerInteraction('I')
                    .open(player);

            assertTrue(panel.isInteractable(0));
            assertFalse(panel.isInteractable(1));
        }
    }
}

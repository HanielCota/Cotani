package com.cotani.gui.button;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cotani.gui.api.Property;
import com.cotani.gui.context.ClickContext;
import com.cotani.gui.state.State;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class ButtonsTest {
    private final Player player = mock(Player.class);
    private final InventoryView view = mock(InventoryView.class);
    private final ItemStack item = mock(ItemStack.class);

    private ClickContext context() {
        return new ClickContext(player, ClickType.LEFT, 4, view);
    }

    @Test
    void shouldClosePlayerInventoryOnCloseButtonClick() {
        Buttons.close().onClick(context());

        verify(player).closeInventory();
    }

    @Test
    void shouldAdvancePageOnNextPageButtonClick() {
        Property<Integer> page = State.of(0);

        Buttons.nextPage(page).onClick(context());

        assertEquals(1, page.get());
    }

    @Test
    void shouldMoveBackPageOnPreviousPageButtonClick() {
        Property<Integer> page = State.of(2);

        Buttons.previousPage(page).onClick(context());
        assertEquals(1, page.get());

        Buttons.previousPage(page).onClick(context());
        assertEquals(0, page.get());

        Buttons.previousPage(page).onClick(context());
        assertEquals(0, page.get());
    }

    @Test
    void shouldExecuteCustomActionWithStaticItem() {
        List<ClickContext> received = new ArrayList<>();
        var button = Buttons.action(item, received::add);

        assertSame(item, button.render(player));
        button.onClick(context());

        assertEquals(List.of(context()), received);
    }

    @Test
    void shouldExecuteCustomActionWithDynamicItem() {
        List<ClickContext> received = new ArrayList<>();
        var button = Buttons.action(_ -> item, received::add);

        assertSame(item, button.render(player));
        button.onClick(context());

        assertEquals(List.of(context()), received);
    }

    @Test
    void shouldClosePlayerInventoryOnCustomCloseButtonClick() {
        Buttons.close(item).onClick(context());

        verify(player).closeInventory();
    }

    @Test
    void shouldAdvancePageOnCustomNextPageButtonClick() {
        Property<Integer> page = State.of(0);
        var button = Buttons.nextPage(page, item);

        assertSame(item, button.render(player));
        button.onClick(context());

        assertEquals(1, page.get());
    }

    @Test
    void shouldMoveBackPageOnCustomPreviousPageButtonClick() {
        Property<Integer> page = State.of(2);
        var button = Buttons.previousPage(page, item);

        assertSame(item, button.render(player));
        button.onClick(context());

        assertEquals(1, page.get());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPageInNextPageButton() {
        assertThrows(NullPointerException.class, () -> Buttons.nextPage(null));
        assertThrows(NullPointerException.class, () -> Buttons.nextPage(null, item));
        assertThrows(NullPointerException.class, () -> Buttons.nextPage(State.of(0), null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullPageInPreviousPageButton() {
        assertThrows(NullPointerException.class, () -> Buttons.previousPage(null));
        assertThrows(NullPointerException.class, () -> Buttons.previousPage(null, item));
        assertThrows(NullPointerException.class, () -> Buttons.previousPage(State.of(0), null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArgumentsInActionButtons() {
        assertThrows(NullPointerException.class, () -> Buttons.close(null));
        assertThrows(NullPointerException.class, () -> Buttons.action((ItemStack) null, _ -> {}));
        assertThrows(NullPointerException.class, () -> Buttons.action(item, null));
        assertThrows(NullPointerException.class, () -> Buttons.action((ItemProvider) null, _ -> {}));
        assertThrows(NullPointerException.class, () -> Buttons.action(_ -> item, null));
    }
}

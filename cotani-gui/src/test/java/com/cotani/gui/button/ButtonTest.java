package com.cotani.gui.button;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.cotani.gui.context.ClickContext;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

final class ButtonTest {
    private final Player viewer = mock(Player.class);
    private final InventoryView view = mock(InventoryView.class);

    private ClickContext context() {
        return new ClickContext(viewer, ClickType.LEFT, 3, view);
    }

    @Test
    void shouldRenderItemProvidedByProvider() {
        ItemStack item = mock(ItemStack.class);
        List<Player> providedFor = new ArrayList<>();
        var button = Button.of(
                player -> {
                    providedFor.add(player);
                    return item;
                },
                _ -> {});

        assertSame(item, button.render(viewer));
        assertEquals(List.of(viewer), providedFor);
    }

    @Test
    void shouldExecuteClickAction() {
        List<ClickContext> received = new ArrayList<>();
        var button = Button.of(_ -> mock(ItemStack.class), received::add);

        button.onClick(context());

        assertEquals(1, received.size());
        assertSame(context().player(), received.get(0).player());
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectProviderReturningNullItem() {
        var button = Button.of(_ -> null, _ -> {});

        assertThrows(NullPointerException.class, () -> button.render(viewer));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullProvider() {
        assertThrows(NullPointerException.class, () -> Button.of(null, _ -> {}));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullClickAction() {
        assertThrows(NullPointerException.class, () -> Button.of(_ -> mock(ItemStack.class), null));
    }

    @Test
    void shouldRenderSameStaticItemForAnyViewerAndIgnoreClicks() {
        ItemStack item = mock(ItemStack.class);
        var button = Button.item(item);

        assertSame(item, button.render(viewer));
        button.onClick(context());

        verifyNoInteractions(view);
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullStaticItem() {
        assertThrows(NullPointerException.class, () -> Button.item(null));
    }

    @Test
    void shouldNotInvokeProviderDuringClick() {
        var provider = mock(ItemProvider.class);
        var button = Button.of(provider, _ -> {});

        button.onClick(context());

        verifyNoInteractions(provider);
    }
}

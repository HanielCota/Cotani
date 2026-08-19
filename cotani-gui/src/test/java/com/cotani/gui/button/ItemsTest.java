package com.cotani.gui.button;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Validation-only tests for {@link Items}. Functional rendering is not testable without a running
 * server because the item builders create real {@code ItemStack} instances through the Paper API.
 */
final class ItemsTest {
    private final Player player = Mockito.mock(Player.class);

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArgumentsInItemFactory() {
        assertThrows(NullPointerException.class, () -> Items.item(null, "titulo"));
        assertThrows(NullPointerException.class, () -> Items.item(Material.STONE, (String) null));
        assertThrows(NullPointerException.class, () -> Items.item(Material.STONE, "titulo", (String[]) null));
        assertThrows(NullPointerException.class, () -> Items.item(null, net.kyori.adventure.text.Component.empty()));
        assertThrows(
                NullPointerException.class,
                () -> Items.item(Material.STONE, (net.kyori.adventure.text.Component) null));
        assertThrows(
                NullPointerException.class,
                () -> Items.item(
                        Material.STONE,
                        net.kyori.adventure.text.Component.empty(),
                        (net.kyori.adventure.text.Component[]) null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullArgumentsInHeadFactory() {
        assertThrows(NullPointerException.class, () -> Items.head(null, "titulo"));
        assertThrows(NullPointerException.class, () -> Items.head(player, (String) null));
        assertThrows(NullPointerException.class, () -> Items.head(player, "titulo", (String[]) null));
        assertThrows(NullPointerException.class, () -> Items.head(null, net.kyori.adventure.text.Component.empty()));
        assertThrows(NullPointerException.class, () -> Items.head(player, (net.kyori.adventure.text.Component) null));
        assertThrows(
                NullPointerException.class,
                () -> Items.head(
                        player, net.kyori.adventure.text.Component.empty(), (net.kyori.adventure.text.Component[])
                                null));
    }

    @Test
    @SuppressWarnings("NullAway")
    void shouldRejectNullBorderPaneMaterial() {
        assertThrows(NullPointerException.class, () -> Items.borderPane(null));
    }
}

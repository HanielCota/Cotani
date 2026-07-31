package com.cotani.gui.button;

import com.cotani.gui.api.Property;
import com.cotani.gui.context.ClickContext;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Static factory helpers for common buttons (close, pagination, generic actions).
 */
public final class Buttons {
    private Buttons() {}

    /**
     * A barrier button that closes the viewer's inventory.
     *
     * @return the close button
     */
    public static Button close() {
        return Button.of(
                _ -> Items.item(Material.BARRIER, "<red>Fechar"),
                context -> context.player().closeInventory());
    }

    /**
     * A button running a custom action with a static item.
     *
     * @param item the item to display
     * @param onClick the click action
     * @return the action button
     */
    public static Button action(ItemStack item, Consumer<ClickContext> onClick) {
        Objects.requireNonNull(item, "Parameter 'item' must not be null");

        return Button.of(_ -> item, onClick);
    }

    /**
     * A button running a custom action with a dynamic item.
     *
     * @param provider the dynamic item supplier
     * @param onClick the click action
     * @return the action button
     */
    public static Button action(ItemProvider provider, Consumer<ClickContext> onClick) {
        return Button.of(provider, onClick);
    }

    /**
     * An arrow button that advances the given page state. Pages beyond the last one are clamped back
     * by the paginated region bound to the same property.
     *
     * @param page the page state property
     * @return the next-page button
     */
    public static Button nextPage(Property<Integer> page) {
        Objects.requireNonNull(page, "Parameter 'page' must not be null");

        return Button.of(_ -> Items.item(Material.ARROW, "<green>Próxima página"), _ -> page.update(p -> p + 1));
    }

    /**
     * An arrow button that moves the given page state back, never below zero.
     *
     * @param page the page state property
     * @return the previous-page button
     */
    public static Button previousPage(Property<Integer> page) {
        Objects.requireNonNull(page, "Parameter 'page' must not be null");

        return Button.of(
                _ -> Items.item(Material.ARROW, "<yellow>Página anterior"), _ -> page.update(p -> Math.max(0, p - 1)));
    }
}

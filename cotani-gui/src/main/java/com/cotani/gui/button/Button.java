package com.cotani.gui.button;

import com.cotani.gui.context.ClickContext;
import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A clickable GUI element: a dynamic item plus a click action.
 *
 * <p>Click debouncing is enforced centrally by the module's anti-exploit guard, so implementations do
 * not need to throttle {@link #onClick(ClickContext)} themselves.
 */
public interface Button {

    /**
     * Renders the item shown to the given viewer. Called on every (re-)render of the slot.
     *
     * @param viewer the player looking at the GUI
     * @return the item to display, never {@code null}
     */
    ItemStack render(Player viewer);

    /**
     * Handles a click on this button. Invoked on the thread that owns the viewer.
     *
     * @param context the immutable click snapshot
     */
    void onClick(ClickContext context);

    /**
     * Creates a button from an item provider and a click action.
     *
     * @param provider the dynamic item supplier
     * @param onClick the click action
     * @return a new button
     */
    static Button of(ItemProvider provider, Consumer<ClickContext> onClick) {
        Objects.requireNonNull(provider, "Parameter 'provider' must not be null");
        Objects.requireNonNull(onClick, "Parameter 'onClick' must not be null");

        return new Button() {
            @Override
            public ItemStack render(Player viewer) {
                return Objects.requireNonNull(provider.provide(viewer), "The item provider must not return null");
            }

            @Override
            public void onClick(ClickContext context) {
                onClick.accept(context);
            }
        };
    }

    /**
     * Creates a static, non-interactive display item.
     *
     * @param item the item to display
     * @return a new button that ignores clicks
     */
    static Button item(ItemStack item) {
        Objects.requireNonNull(item, "Parameter 'item' must not be null");

        return of(_ -> item, _ -> {});
    }
}

package com.cotani.gui.api;

import com.cotani.gui.button.Button;
import com.cotani.gui.context.ClickContext;
import com.cotani.gui.context.CloseContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Active GUI instance bound to a single viewer's open inventory.
 *
 * <p>Instances are created by {@link GuiWindow#open(Player)} and identified by the anti-exploit guard
 * through {@link InventoryHolder}. All rendering happens per-slot (zero-flicker) on the thread that
 * triggered the state change; mutating bound properties must therefore happen on the thread that owns
 * the viewer.
 */
public final class GuiPanel implements InventoryHolder {
    private final Player viewer;
    private final int rows;
    private final @Nullable ItemStack borderItem;
    private final Consumer<CloseContext> closeHandler;
    private final Map<Integer, Button> buttons = new HashMap<>();
    private final Set<Integer> dynamicSlots = new HashSet<>();
    private final Set<Integer> interactableSlots = new HashSet<>();
    private final List<Consumer<GuiPanel>> regionRenderers = new ArrayList<>();
    private final List<Property.Subscription> subscriptions = new ArrayList<>();
    private final AtomicBoolean disposed = new AtomicBoolean();
    private final Inventory inventory;

    private GuiPanel(
            Player viewer,
            Component title,
            int rows,
            @Nullable ItemStack borderItem,
            Consumer<CloseContext> closeHandler) {
        this.viewer = viewer;
        this.rows = rows;
        this.borderItem = borderItem;
        this.closeHandler = closeHandler;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    static GuiPanel create(
            Player viewer,
            Component title,
            int rows,
            @Nullable ItemStack borderItem,
            Consumer<CloseContext> closeHandler) {
        return new GuiPanel(viewer, title, rows, borderItem, closeHandler);
    }

    /**
     * Returns the id of the player viewing this panel.
     *
     * @return the viewer id
     */
    public UUID viewerId() {
        return viewer.getUniqueId();
    }

    /**
     * Returns whether this panel was already disposed (closed).
     *
     * @return {@code true} after the close handler ran
     */
    public boolean isDisposed() {
        return disposed.get();
    }

    /**
     * Returns whether the specified slot is marked as interactable (allowing player item transfers).
     *
     * @param slot the slot index
     * @return {@code true} if the slot is interactable
     */
    public boolean isInteractable(int slot) {
        return interactableSlots.contains(slot);
    }

    /**
     * Marks the specified slot as interactable, allowing player item transfers.
     *
     * @param slot the slot index
     */
    public void markInteractable(int slot) {
        interactableSlots.add(slot);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Re-renders every slot without rebuilding the inventory, avoiding flicker.
     */
    public void render() {
        for (var slot = 0; slot < inventory.getSize(); slot++) {
            if (!dynamicSlots.contains(slot)) {
                renderSlot(slot);
            }
        }

        for (var regionRenderer : regionRenderers) {
            regionRenderer.accept(this);
        }
    }

    /**
     * Closes the viewer's inventory, which triggers the close pipeline exactly once.
     */
    public void close() {
        viewer.closeInventory();
    }

    /**
     * Internal dispatch invoked by the anti-exploit guard after debounce validation.
     *
     * @param context the click snapshot
     */
    public void handleClick(ClickContext context) {
        if (disposed.get()) {
            return;
        }

        var button = buttons.get(context.slot());

        if (button != null) {
            button.onClick(context);
        }
    }

    /**
     * Internal close dispatch invoked by the anti-exploit guard. Runs the close handler and releases
     * property subscriptions exactly once.
     */
    public void handleClose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }

        for (var subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();

        closeHandler.accept(new CloseContext(viewer));
    }

    void bindSlot(int slot, Button button) {
        buttons.put(slot, button);
    }

    void observe(Property<?> property, List<Integer> slots) {
        subscriptions.add(property.observe(_ -> renderSlots(slots)));
    }

    void addRegion(PaginatedRegion<?> region) {
        regionRenderers.add(region::renderInto);
        subscriptions.add(region.page().observe(_ -> region.renderInto(this)));
    }

    void setDynamicSlot(int slot, Button button) {
        dynamicSlots.add(slot);
        buttons.put(slot, button);

        var newItem = button.render(viewer);

        if (!Objects.equals(inventory.getItem(slot), newItem)) {
            inventory.setItem(slot, newItem);
        }
    }

    void clearDynamicSlot(int slot) {
        dynamicSlots.add(slot);
        buttons.remove(slot);

        if (!Objects.equals(inventory.getItem(slot), borderItem)) {
            inventory.setItem(slot, borderItem);
        }
    }

    private void renderSlots(List<Integer> slots) {
        for (var slot : slots) {
            if (!dynamicSlots.contains(slot)) {
                renderSlot(slot);
            }
        }
    }

    private void renderSlot(int slot) {
        var button = buttons.get(slot);
        var newItem = button != null ? button.render(viewer) : borderItem;

        if (!Objects.equals(inventory.getItem(slot), newItem)) {
            inventory.setItem(slot, newItem);
        }
    }

    @Override
    public String toString() {
        return "GuiPanel{viewer=" + viewer.getName() + ", rows=" + rows + ", disposed=" + disposed.get() + '}';
    }
}

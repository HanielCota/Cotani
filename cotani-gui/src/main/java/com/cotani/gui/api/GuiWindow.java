package com.cotani.gui.api;

import com.cotani.gui.button.Button;
import com.cotani.gui.button.Items;
import com.cotani.gui.context.CloseContext;
import com.cotani.gui.layout.Structure;
import com.cotani.text.MiniMessages;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Fluent builder for declarative, reactive inventory GUIs.
 *
 * <p>Must be used on the thread that owns the viewer (main thread on Paper, the entity region thread
 * on Folia). The module's {@code CotaniGuiModule} listener must be registered for clicks to work.
 */
public final class GuiWindow {
    private final Component title;
    private final Map<Character, Button> buttons = new LinkedHashMap<>();
    private final List<Character> interactableSymbols = new ArrayList<>();
    private final List<ObservedBinding> observedBindings = new ArrayList<>();
    private final List<PaginatedSpec<?>> paginatedSpecs = new ArrayList<>();

    private @Nullable Structure structure;
    private @Nullable Material borderMaterial;
    private Consumer<CloseContext> closeHandler = _ -> {};

    private GuiWindow(Component title) {
        this.title = title;
    }

    /**
     * Creates a window with a MiniMessage title.
     *
     * @param title the MiniMessage title
     * @return a new window builder
     */
    public static GuiWindow panel(String title) {
        Objects.requireNonNull(title, "Parameter 'title' must not be null");

        return new GuiWindow(MiniMessages.parse(title));
    }

    /**
     * Creates a window with a component title.
     *
     * @param title the title component
     * @return a new window builder
     */
    public static GuiWindow panel(Component title) {
        Objects.requireNonNull(title, "Parameter 'title' must not be null");

        return new GuiWindow(title);
    }

    /**
     * Declares the matrix layout. The number of rows is derived from the patterns.
     *
     * @param rows the row patterns, e.g. {@code "# # #"}
     * @return this builder
     */
    public GuiWindow structure(String... rows) {
        this.structure = Structure.parse(rows);
        return this;
    }

    /**
     * Declared for fluent readability; the row count always comes from {@link #structure(String...)}.
     * When both are present they must match.
     *
     * @param rows the row count, between 1 and 6
     * @return this builder
     */
    public GuiWindow rows(int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be between 1 and 6: " + rows);
        }

        if (structure != null && structure.rows() != rows) {
            throw new IllegalArgumentException(
                    "rows(" + rows + ") does not match the structure row count (" + structure.rows() + ')');
        }

        return this;
    }

    /**
     * Fills every slot without a binding with a decorative pane of the given material.
     *
     * @param material the pane material
     * @return this builder
     */
    public GuiWindow border(Material material) {
        this.borderMaterial = Objects.requireNonNull(material, "Parameter 'material' must not be null");
        return this;
    }

    /**
     * Binds a structure symbol to a clickable button.
     *
     * @param symbol the structure symbol ({@code '.'} is reserved)
     * @param button the button
     * @return this builder
     */
    public GuiWindow bind(char symbol, Button button) {
        requireBindableSymbol(symbol);
        Objects.requireNonNull(button, "Parameter 'button' must not be null");

        buttons.put(symbol, button);
        return this;
    }

    /**
     * Binds a structure symbol to a static display item.
     *
     * @param symbol the structure symbol ({@code '.'} is reserved)
     * @param item the item to display
     * @return this builder
     */
    public GuiWindow bind(char symbol, ItemStack item) {
        Objects.requireNonNull(item, "Parameter 'item' must not be null");

        return bind(symbol, Button.item(item));
    }

    /**
     * Binds a structure symbol to an ON/OFF toggle backed by a boolean property with custom labels.
     *
     * @param symbol the structure symbol
     * @param state the boolean state property
     * @param icon the item material
     * @param title the MiniMessage display name
     * @param onLabel MiniMessage lore line when active
     * @param offLabel MiniMessage lore line when inactive
     * @param onChange callback invoked with the new value after each toggle
     * @return this builder
     */
    public GuiWindow bindToggle(
            char symbol,
            Property<Boolean> state,
            Material icon,
            String title,
            String onLabel,
            String offLabel,
            Consumer<Boolean> onChange) {
        Objects.requireNonNull(state, "Parameter 'state' must not be null");
        Objects.requireNonNull(icon, "Parameter 'icon' must not be null");
        Objects.requireNonNull(title, "Parameter 'title' must not be null");
        Objects.requireNonNull(onLabel, "Parameter 'onLabel' must not be null");
        Objects.requireNonNull(offLabel, "Parameter 'offLabel' must not be null");
        Objects.requireNonNull(onChange, "Parameter 'onChange' must not be null");

        var button =
                Button.of(_ -> Items.item(icon, title, Boolean.TRUE.equals(state.get()) ? onLabel : offLabel), _ -> {
                    state.update(on -> !Boolean.TRUE.equals(on));
                    onChange.accept(state.get());
                });

        bind(symbol, button);
        observedBindings.add(new ObservedBinding(state, symbol));
        return this;
    }

    /**
     * Binds a structure symbol to an ON/OFF toggle backed by a boolean property with default labels.
     *
     * @param symbol the structure symbol
     * @param state the boolean state property
     * @param icon the item material
     * @param title the MiniMessage display name
     * @param onChange callback invoked with the new value after each toggle
     * @return this builder
     */
    public GuiWindow bindToggle(
            char symbol, Property<Boolean> state, Material icon, String title, Consumer<Boolean> onChange) {
        return bindToggle(symbol, state, icon, title, "<green>ATIVADO", "<red>DESATIVADO", onChange);
    }

    /**
     * Binds a structure symbol to a paginated collection. Every slot assigned to the symbol displays
     * one item of the current page; page changes re-render the region.
     *
     * @param symbol the structure symbol
     * @param pageState the zero-based page state property
     * @param items the full item list
     * @param renderer maps an item to its button
     * @param <T> the item type
     * @return this builder
     */
    public <T> GuiWindow paginated(
            char symbol, Property<Integer> pageState, List<T> items, Function<T, Button> renderer) {
        requireBindableSymbol(symbol);
        Objects.requireNonNull(pageState, "Parameter 'pageState' must not be null");
        Objects.requireNonNull(items, "Parameter 'items' must not be null");
        Objects.requireNonNull(renderer, "Parameter 'renderer' must not be null");

        paginatedSpecs.add(new PaginatedSpec<>(symbol, pageState, List.copyOf(items), renderer));
        return this;
    }

    /**
     * Registers a callback invoked exactly once when the viewer closes the GUI.
     *
     * @param handler the close callback
     * @return this builder
     */
    public GuiWindow onClose(Consumer<CloseContext> handler) {
        this.closeHandler = Objects.requireNonNull(handler, "Parameter 'handler' must not be null");
        return this;
    }

    /**
     * Marks every slot assigned to the given symbol as interactable, allowing players to place and
     * remove items directly while still protecting all other panel slots.
     *
     * @param symbol the structure symbol
     * @return this builder
     */
    public GuiWindow allowPlayerInteraction(char symbol) {
        requireBindableSymbol(symbol);
        interactableSymbols.add(symbol);
        return this;
    }

    /**
     * Opens the GUI for the given player.
     *
     * <p>Must be called on the thread that owns the player (main thread on Paper, the entity region
     * thread on Folia).
     *
     * @param player the viewer
     * @return the active panel
     */
    public GuiPanel open(Player player) {
        Objects.requireNonNull(player, "Parameter 'player' must not be null");

        var layout = Objects.requireNonNull(structure, "Call structure(...) before open()");
        validateSymbols(layout);

        var borderItem = borderMaterial == null ? null : Items.borderPane(borderMaterial);
        var panel = GuiPanel.create(player, title, layout.rows(), borderItem, closeHandler);

        for (var symbol : interactableSymbols) {
            for (var slot : layout.slots(symbol)) {
                panel.markInteractable(slot);
            }
        }

        for (var entry : buttons.entrySet()) {
            for (var slot : layout.slots(entry.getKey())) {
                panel.bindSlot(slot, entry.getValue());
            }
        }

        for (var observed : observedBindings) {
            panel.observe(observed.property(), layout.slots(observed.symbol()));
        }

        for (var spec : paginatedSpecs) {
            panel.addRegion(spec.toRegion(layout.slots(spec.symbol())));
        }

        panel.render();
        player.openInventory(panel.getInventory());

        return panel;
    }

    private void validateSymbols(Structure layout) {
        for (var symbol : buttons.keySet()) {
            requirePresentSymbol(layout, symbol);
        }
        for (var symbol : interactableSymbols) {
            requirePresentSymbol(layout, symbol);
        }
        for (var spec : paginatedSpecs) {
            requirePresentSymbol(layout, spec.symbol());
        }
    }

    private static void requirePresentSymbol(Structure layout, char symbol) {
        if (layout.slots(symbol).isEmpty()) {
            throw new IllegalArgumentException("Symbol '" + symbol + "' is not present in the structure");
        }
    }

    private static void requireBindableSymbol(char symbol) {
        if (symbol == Structure.EMPTY) {
            throw new IllegalArgumentException("Symbol '.' is reserved for empty slots");
        }
    }

    private record ObservedBinding(Property<?> property, char symbol) {}

    private record PaginatedSpec<T>(
            char symbol, Property<Integer> pageState, List<T> items, Function<T, Button> renderer) {
        PaginatedRegion<T> toRegion(List<Integer> slots) {
            return new PaginatedRegion<>(slots, pageState, items, renderer);
        }
    }
}

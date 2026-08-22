package com.cotani.hud.api;

import com.cotani.gui.api.Property;
import java.util.function.Function;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Fluent builder for creating per-player {@link Sidebar} instances.
 */
public interface SidebarBuilder {

    /**
     * Sets the static title of the sidebar.
     *
     * @param title the title component
     * @return this builder
     */
    SidebarBuilder title(Component title);

    /**
     * Sets the static title of the sidebar from a MiniMessage string.
     *
     * @param miniMessage title MiniMessage format
     * @return this builder
     */
    default SidebarBuilder title(String miniMessage) {
        return title(com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Sets a dynamic title provider.
     *
     * @param titleSupplier supplier returning the title component
     * @return this builder
     */
    SidebarBuilder title(Supplier<Component> titleSupplier);

    /**
     * Binds the title to a reactive property.
     *
     * @param property reactive property
     * @param mapper mapper from property value to component
     * @param <T> value type
     * @return this builder
     */
    <T> SidebarBuilder bindTitle(Property<T> property, Function<T, Component> mapper);

    /**
     * Sets a static line at the given score index.
     *
     * @param score the score index (1-15)
     * @param content line component
     * @return this builder
     */
    SidebarBuilder line(int score, Component content);

    /**
     * Sets a static line at the given score index from a MiniMessage string.
     *
     * @param score the score index (1-15)
     * @param miniMessage line MiniMessage string
     * @return this builder
     */
    default SidebarBuilder line(int score, String miniMessage) {
        return line(score, com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Sets a dynamic line provider evaluated per player.
     *
     * @param score the score index (1-15)
     * @param provider provider function taking the viewer player
     * @return this builder
     */
    SidebarBuilder line(int score, Function<Player, Component> provider);

    /**
     * Sets a dynamic line provider.
     *
     * @param score the score index (1-15)
     * @param supplier supplier returning line component
     * @return this builder
     */
    SidebarBuilder line(int score, Supplier<Component> supplier);

    /**
     * Binds a line to a reactive property.
     *
     * @param score the score index (1-15)
     * @param property the reactive property
     * @param mapper mapper function
     * @param <T> value type
     * @return this builder
     */
    <T> SidebarBuilder bindLine(int score, Property<T> property, Function<T, Component> mapper);

    /**
     * Builds and attaches the sidebar to the given player.
     *
     * @param player the target player
     * @return the active sidebar instance
     */
    Sidebar apply(Player player);
}

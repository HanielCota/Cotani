package com.cotani.hud.api;

import com.cotani.gui.api.Property;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Manager for player tab list header and footer presentations.
 */
public interface TabListManager {

    /**
     * Sets the header for a player.
     *
     * @param player target player
     * @param header header component
     */
    void setHeader(Player player, Component header);

    /**
     * Sets the header for a player from a MiniMessage string.
     *
     * @param player target player
     * @param miniMessage header MiniMessage string
     */
    default void setHeader(Player player, String miniMessage) {
        setHeader(player, com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Sets the footer for a player.
     *
     * @param player target player
     * @param footer footer component
     */
    void setFooter(Player player, Component footer);

    /**
     * Sets the footer for a player from a MiniMessage string.
     *
     * @param player target player
     * @param miniMessage footer MiniMessage string
     */
    default void setFooter(Player player, String miniMessage) {
        setFooter(player, com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Sets both header and footer for a player.
     *
     * @param player target player
     * @param header header component
     * @param footer footer component
     */
    void setHeaderAndFooter(Player player, Component header, Component footer);

    /**
     * Sets both header and footer for a player from MiniMessage strings.
     *
     * @param player target player
     * @param headerMiniMessage header MiniMessage string
     * @param footerMiniMessage footer MiniMessage string
     */
    default void setHeaderAndFooter(Player player, String headerMiniMessage, String footerMiniMessage) {
        setHeaderAndFooter(
                player,
                com.cotani.text.MiniMessages.parse(headerMiniMessage),
                com.cotani.text.MiniMessages.parse(footerMiniMessage));
    }

    /**
     * Binds the tab list header to a reactive property.
     *
     * @param player target player
     * @param property reactive property
     * @param mapper mapper function
     * @param <T> property type
     * @return subscription handle
     */
    <T> Property.Subscription bindHeader(Player player, Property<T> property, Function<T, Component> mapper);

    /**
     * Binds the tab list footer to a reactive property.
     *
     * @param player target player
     * @param property reactive property
     * @param mapper mapper function
     * @param <T> property type
     * @return subscription handle
     */
    <T> Property.Subscription bindFooter(Player player, Property<T> property, Function<T, Component> mapper);

    /**
     * Clears tab list header and footer for the player.
     *
     * @param player target player
     */
    void clear(Player player);
}

package com.cotani.hud.api;

import com.cotani.gui.api.Property;
import java.time.Duration;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Manager for sending and maintaining player action bar displays.
 */
public interface ActionBarController {

    /**
     * Sends an action bar message once.
     *
     * @param player target player
     * @param message component message
     */
    void send(Player player, Component message);

    /**
     * Sends an action bar message once from a MiniMessage string.
     *
     * @param player target player
     * @param miniMessage MiniMessage string
     */
    default void send(Player player, String miniMessage) {
        send(player, com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Sends and maintains an action bar message for the specified duration.
     *
     * @param player target player
     * @param message message component
     * @param duration duration to hold the action bar visible
     */
    void sendTimed(Player player, Component message, Duration duration);

    /**
     * Sends and maintains an action bar message for the specified duration from a MiniMessage string.
     *
     * @param player target player
     * @param miniMessage message MiniMessage string
     * @param duration duration to hold the action bar visible
     */
    default void sendTimed(Player player, String miniMessage, Duration duration) {
        sendTimed(player, com.cotani.text.MiniMessages.parse(miniMessage), duration);
    }

    /**
     * Binds the action bar to a reactive property.
     *
     * @param player target player
     * @param property reactive property
     * @param mapper mapper function
     * @param <T> property type
     * @return subscription handle
     */
    <T> Property.Subscription bind(Player player, Property<T> property, Function<T, Component> mapper);

    /**
     * Clears any active timed or bound action bar for the player.
     *
     * @param player target player
     */
    void clear(Player player);
}

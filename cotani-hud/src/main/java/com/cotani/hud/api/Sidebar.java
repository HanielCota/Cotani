package com.cotani.hud.api;

import com.cotani.gui.api.Property;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Represents an active per-player sidebar (scoreboard) display.
 */
public interface Sidebar extends AutoCloseable {

    /**
     * Returns the unique identifier of the viewer player.
     *
     * @return the player UUID
     */
    UUID viewerId();

    /**
     * Returns the active viewer player if online.
     *
     * @return an optional containing the online player, or empty if disconnected
     */
    Optional<Player> viewer();

    /**
     * Updates the title of the sidebar immediately.
     *
     * @param title the new title component
     * @return this sidebar instance
     */
    Sidebar title(Component title);

    /**
     * Updates the title of the sidebar immediately from a MiniMessage string.
     *
     * @param miniMessage the new title MiniMessage string
     * @return this sidebar instance
     */
    default Sidebar title(String miniMessage) {
        return title(com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Updates a line score value immediately.
     *
     * @param score the line score (typically 1-15)
     * @param content the line content component
     * @return this sidebar instance
     */
    Sidebar line(int score, Component content);

    /**
     * Updates a line score value immediately from a MiniMessage string.
     *
     * @param score the line score (typically 1-15)
     * @param miniMessage the line MiniMessage string
     * @return this sidebar instance
     */
    default Sidebar line(int score, String miniMessage) {
        return line(score, com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Removes a line by score.
     *
     * @param score the score number to remove
     * @return this sidebar instance
     */
    Sidebar removeLine(int score);

    /**
     * Binds a line to a reactive {@link Property}, updating automatically on changes.
     *
     * @param score the line score (1-15)
     * @param property the reactive property
     * @param mapper mapper from value to component
     * @param <T> property type
     * @return this sidebar instance
     */
    <T> Sidebar bindLine(int score, Property<T> property, Function<T, Component> mapper);

    /**
     * Refreshes all lines and title on the player's thread.
     */
    void refresh();

    /**
     * Returns whether this sidebar is destroyed.
     *
     * @return true if destroyed
     */
    boolean isDestroyed();

    /**
     * Destroys this sidebar and detaches the scoreboard from the player.
     */
    @Override
    void close();
}

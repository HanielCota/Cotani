package com.cotani.hud.api;

import com.cotani.gui.api.Property;
import java.util.Set;
import java.util.function.Function;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Represents an active, managed BossBar instance.
 */
public interface HudBossBar extends AutoCloseable {

    /**
     * Returns the underlying Adventure BossBar.
     *
     * @return the Adventure BossBar
     */
    BossBar adventureBar();

    /**
     * Updates the bar title.
     *
     * @param title the new title
     * @return this instance
     */
    HudBossBar title(Component title);

    /**
     * Updates the bar title from a MiniMessage string.
     *
     * @param miniMessage the new title MiniMessage string
     * @return this instance
     */
    default HudBossBar title(String miniMessage) {
        return title(com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Updates the bar progress (0.0 to 1.0).
     *
     * @param progress progress value
     * @return this instance
     */
    HudBossBar progress(float progress);

    /**
     * Updates the bar color.
     *
     * @param color boss bar color
     * @return this instance
     */
    HudBossBar color(BossBar.Color color);

    /**
     * Updates the bar overlay.
     *
     * @param overlay boss bar overlay
     * @return this instance
     */
    HudBossBar overlay(BossBar.Overlay overlay);

    /**
     * Shows the boss bar to a player.
     *
     * @param player the viewer
     * @return this instance
     */
    HudBossBar show(Player player);

    /**
     * Hides the boss bar from a player.
     *
     * @param player the viewer
     * @return this instance
     */
    HudBossBar hide(Player player);

    /**
     * Returns all current viewers that are currently online.
     *
     * @return set of viewing players
     */
    Set<Player> viewers();

    /**
     * Returns the UUIDs of all current viewers.
     *
     * @return set of viewing player UUIDs
     */
    Set<java.util.UUID> viewerIds();

    /**
     * Binds progress to a reactive property.
     *
     * @param property progress property
     * @return this instance
     */
    HudBossBar bindProgress(Property<Float> property);

    /**
     * Binds title to a reactive property.
     *
     * @param property reactive property
     * @param mapper mapper function
     * @param <T> property type
     * @return this instance
     */
    <T> HudBossBar bindTitle(Property<T> property, Function<T, Component> mapper);

    /**
     * Returns whether this boss bar is destroyed.
     *
     * @return true if destroyed
     */
    boolean isDestroyed();

    /**
     * Destroys this boss bar and hides it from all viewers.
     */
    @Override
    void close();
}

package com.cotani.hud.api;

import java.time.Duration;
import java.util.Set;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Fluent builder for creating {@link HudBossBar} instances.
 */
public interface BossBarBuilder {

    /**
     * Sets the title of the boss bar.
     *
     * @param title the title component
     * @return this builder
     */
    BossBarBuilder title(Component title);

    /**
     * Sets the title of the boss bar from a MiniMessage string.
     *
     * @param miniMessage title MiniMessage string
     * @return this builder
     */
    default BossBarBuilder title(String miniMessage) {
        return title(com.cotani.text.MiniMessages.parse(miniMessage));
    }

    /**
     * Sets the color of the boss bar.
     *
     * @param color color
     * @return this builder
     */
    BossBarBuilder color(BossBar.Color color);

    /**
     * Sets the overlay of the boss bar.
     *
     * @param overlay overlay style
     * @return this builder
     */
    BossBarBuilder overlay(BossBar.Overlay overlay);

    /**
     * Sets the initial progress (0.0 to 1.0).
     *
     * @param progress progress float
     * @return this builder
     */
    BossBarBuilder progress(float progress);

    /**
     * Sets the boss bar flags.
     *
     * @param flags boss bar flags
     * @return this builder
     */
    BossBarBuilder flags(Set<BossBar.Flag> flags);

    /**
     * Configures a countdown timer that decrements progress to 0 and closes the bar when finished.
     *
     * @param duration total countdown duration
     * @return this builder
     */
    BossBarBuilder countdown(Duration duration);

    /**
     * Builds and registers the managed boss bar.
     *
     * @return active HudBossBar
     */
    HudBossBar build();

    /**
     * Builds, registers, and shows the boss bar to the specified player.
     *
     * @param player target viewer
     * @return active HudBossBar
     */
    HudBossBar show(Player player);
}

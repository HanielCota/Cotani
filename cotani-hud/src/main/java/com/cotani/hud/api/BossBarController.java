package com.cotani.hud.api;

import java.util.Set;
import org.bukkit.entity.Player;

/**
 * Manager for creating and tracking active {@link HudBossBar} instances.
 */
public interface BossBarController {

    /**
     * Creates a new fluent boss bar builder.
     *
     * @return a new builder
     */
    BossBarBuilder builder();

    /**
     * Returns all active managed boss bars for a given player.
     *
     * @param player target player
     * @return set of boss bars visible to the player
     */
    Set<HudBossBar> getBars(Player player);

    /**
     * Hides and removes all boss bars for a player.
     *
     * @param player target player
     */
    void clear(Player player);
}

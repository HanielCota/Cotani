package com.cotani.hud.api;

import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Central Cotani HUD module providing sidebars, tablist, bossbars, and actionbars.
 */
public interface HudModule extends AutoCloseable {

    /**
     * Creates a new fluent {@link SidebarBuilder}.
     *
     * @return a new builder
     */
    SidebarBuilder sidebar();

    /**
     * Returns the TabList manager.
     *
     * @return the TabList manager
     */
    TabListController tabList();

    /**
     * Returns the BossBar manager.
     *
     * @return the BossBar manager
     */
    BossBarController bossBar();

    /**
     * Returns the ActionBar manager.
     *
     * @return the ActionBar manager
     */
    ActionBarController actionBar();

    /**
     * Returns the active sidebar for a player if present.
     *
     * @param player target player
     * @return optional containing the active sidebar
     */
    Optional<Sidebar> getSidebar(Player player);

    /**
     * Returns the active sidebar for a player UUID if present.
     *
     * @param playerId player UUID
     * @return optional containing the active sidebar
     */
    Optional<Sidebar> getSidebar(UUID playerId);

    /**
     * Clears all HUD components (sidebar, tablist, bossbars, actionbar) for the given player.
     *
     * @param player target player
     */
    void clear(Player player);

    /**
     * Disposes the module, clearing all HUD elements and unregistering listeners.
     */
    @Override
    void close();
}

package com.cotani.hud.impl;

import com.cotani.api.InternalApi;
import com.cotani.hud.api.ActionBarManager;
import com.cotani.hud.api.BossBarManager;
import com.cotani.hud.api.HudModule;
import com.cotani.hud.api.Sidebar;
import com.cotani.hud.api.SidebarBuilder;
import com.cotani.hud.api.TabListManager;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;

/**
 * Default implementation of {@link HudModule}.
 */
@InternalApi
public final class DefaultHudModule implements HudModule {

    private static final String PLAYER_NULL_MSG = "Parameter 'player' must not be null";

    private final PaperTaskScheduler scheduler;
    private final DefaultTabListManager tabListManager;
    private final DefaultBossBarManager bossBarManager;
    private final DefaultActionBarManager actionBarManager;
    private final HudPlayerQuitListener quitListener;
    private final Map<UUID, Sidebar> sidebars = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultHudModule(Plugin plugin, PaperTaskScheduler scheduler) {
        Objects.requireNonNull(plugin, "Parameter 'plugin' must not be null");
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");

        this.tabListManager = new DefaultTabListManager(scheduler);
        this.bossBarManager = new DefaultBossBarManager(scheduler);
        this.actionBarManager = new DefaultActionBarManager(scheduler);
        this.quitListener = new HudPlayerQuitListener(this);

        plugin.getServer().getPluginManager().registerEvents(quitListener, plugin);
    }

    @Override
    public SidebarBuilder sidebar() {
        return new DefaultSidebarBuilder(scheduler, this::registerSidebar, sidebars::remove);
    }

    private void registerSidebar(Sidebar sidebar) {
        var previous = sidebars.put(sidebar.viewerId(), sidebar);
        if (previous != null && !Objects.equals(previous, sidebar)) {
            previous.close();
        }
    }

    @Override
    public TabListManager tabList() {
        return tabListManager;
    }

    @Override
    public BossBarManager bossBar() {
        return bossBarManager;
    }

    @Override
    public ActionBarManager actionBar() {
        return actionBarManager;
    }

    @Override
    public Optional<Sidebar> getSidebar(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        return getSidebar(player.getUniqueId());
    }

    @Override
    public Optional<Sidebar> getSidebar(UUID playerId) {
        Objects.requireNonNull(playerId, "Parameter 'playerId' must not be null");
        return Optional.ofNullable(sidebars.get(playerId));
    }

    @Override
    public void clear(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        var sidebar = sidebars.remove(player.getUniqueId());
        if (sidebar != null) {
            sidebar.close();
        }
        tabListManager.clear(player);
        bossBarManager.clear(player);
        actionBarManager.clear(player);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        HandlerList.unregisterAll(quitListener);

        for (var sidebar : sidebars.values()) {
            try {
                sidebar.close();
            } catch (Exception exception) {
                java.util.logging.Logger.getLogger(DefaultHudModule.class.getName())
                        .log(java.util.logging.Level.FINE, "Could not close sidebar", exception);
            }
        }
        sidebars.clear();

        bossBarManager.close();
        actionBarManager.close();
        tabListManager.close();
    }
}

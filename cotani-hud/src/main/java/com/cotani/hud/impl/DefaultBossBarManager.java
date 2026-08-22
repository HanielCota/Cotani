package com.cotani.hud.impl;

import com.cotani.api.InternalApi;
import com.cotani.hud.api.BossBarBuilder;
import com.cotani.hud.api.BossBarManager;
import com.cotani.hud.api.HudBossBar;
import com.cotani.task.api.PaperTaskScheduler;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.bukkit.entity.Player;

/**
 * Default implementation of {@link BossBarManager}.
 */
@InternalApi
public final class DefaultBossBarManager implements BossBarManager {

    private static final String PLAYER_NULL_MSG = "Parameter 'player' must not be null";

    private final PaperTaskScheduler scheduler;
    private final Set<HudBossBar> activeBars = ConcurrentHashMap.newKeySet();

    public DefaultBossBarManager(PaperTaskScheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "Parameter 'scheduler' must not be null");
    }

    @Override
    public BossBarBuilder builder() {
        return new DefaultBossBarBuilder(scheduler, activeBars::add, activeBars::remove);
    }

    @Override
    public Set<HudBossBar> getBars(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        var uuid = player.getUniqueId();
        return activeBars.stream()
                .filter(bar -> bar.viewerIds().contains(uuid))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void clear(Player player) {
        Objects.requireNonNull(player, PLAYER_NULL_MSG);
        var uuid = player.getUniqueId();
        for (var bar : activeBars) {
            if (bar.viewerIds().contains(uuid)) {
                bar.hide(player);
            }
        }
    }

    /**
     * Closes and clears all active boss bars.
     */
    public void close() {
        for (var bar : activeBars) {
            try {
                bar.close();
            } catch (Exception _) {
                // Suppress
            }
        }
        activeBars.clear();
    }
}

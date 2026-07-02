package com.cotani.user.internal.listener;

import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.user.internal.service.InternalUserService;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public final class UserListener implements Listener {

    private final Plugin plugin;
    private final InternalUserService userService;
    private final PaperTaskScheduler scheduler;
    private final Component loadFailureMessage;

    public UserListener(
            Plugin plugin,
            InternalUserService userService,
            PaperTaskScheduler scheduler,
            Component loadFailureMessage) {
        this.plugin = plugin;
        this.userService = userService;
        this.scheduler = scheduler;
        this.loadFailureMessage = loadFailureMessage;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        String username = player.getName();

        userService
                .load(uniqueId, username)
                .thenAccept(user -> scheduler.global("user-load-complete", () -> {
                    Player onlinePlayer = Bukkit.getPlayer(uniqueId);

                    if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                        var _ = userService.unload(uniqueId).exceptionally(throwable -> {
                            plugin.getLogger().log(Level.SEVERE, throwable, () -> "Failed to unload user " + uniqueId);
                            return null;
                        });
                    }
                }))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, throwable, () -> "Failed to load user " + uniqueId);

                    scheduler.global("user-load-failed", () -> {
                        Player onlinePlayer = Bukkit.getPlayer(uniqueId);

                        if (onlinePlayer != null && onlinePlayer.isOnline()) {
                            onlinePlayer.kick(loadFailureMessage);
                        }
                    });

                    return null;
                });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();

        userService.unload(uniqueId).exceptionally(throwable -> {
            plugin.getLogger().log(Level.SEVERE, throwable, () -> "Failed to unload user " + uniqueId);
            return null;
        });
    }
}

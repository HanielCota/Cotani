package com.cotani.user.internal.listener;

import com.cotani.api.InternalApi;
import com.cotani.task.api.PaperTaskScheduler;
import com.cotani.task.util.VoidResult;
import com.cotani.user.internal.service.InternalUserService;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

@InternalApi
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
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.userService = Objects.requireNonNull(userService, "userService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.loadFailureMessage = Objects.requireNonNull(loadFailureMessage, "loadFailureMessage");
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID uniqueId = event.getUniqueId();
        String username = event.getName();

        try {
            userService.load(uniqueId, username);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, exception, () -> "Failed to pre-load user " + uniqueId);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uniqueId = player.getUniqueId();
        String username = player.getName();

        userService
                .load(uniqueId, username)
                .thenAccept(user -> scheduler.global("user-load-complete", () -> {
                    try {
                        Player onlinePlayer = Bukkit.getPlayer(uniqueId);

                        if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                            var _ = userService.unload(uniqueId).exceptionally(throwable -> {
                                plugin.getLogger()
                                        .log(Level.SEVERE, throwable, () -> "Failed to unload user " + uniqueId);
                                return VoidResult.nullValue();
                            });
                        }
                    } catch (RuntimeException exception) {
                        plugin.getLogger()
                                .log(Level.SEVERE, exception, () -> "Error in user-load-complete task for " + uniqueId);
                    }
                }))
                .exceptionally(throwable -> {
                    Throwable cause = throwable instanceof java.util.concurrent.CompletionException
                                    && throwable.getCause() != null
                            ? throwable.getCause()
                            : throwable;
                    plugin.getLogger().log(Level.SEVERE, cause, () -> "Failed to load user " + uniqueId);

                    scheduler.global("user-load-failed", () -> {
                        try {
                            Player onlinePlayer = Bukkit.getPlayer(uniqueId);

                            if (onlinePlayer != null && onlinePlayer.isOnline()) {
                                onlinePlayer.kick(loadFailureMessage);
                            }
                        } catch (RuntimeException exception) {
                            plugin.getLogger()
                                    .log(
                                            Level.SEVERE,
                                            exception,
                                            () -> "Error in user-load-failed task for " + uniqueId);
                        }
                    });

                    return VoidResult.nullValue();
                });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();

        userService
                .unload(uniqueId)
                .toCompletableFuture()
                .orTimeout(10, TimeUnit.SECONDS)
                .exceptionally(throwable -> {
                    plugin.getLogger().log(Level.SEVERE, throwable, () -> "Failed to unload user " + uniqueId);
                    return VoidResult.nullValue();
                });
    }
}
